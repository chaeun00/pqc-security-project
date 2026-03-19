import base64
import json
import os
from typing import Optional

import oqs
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from fastapi import APIRouter, Header, HTTPException

from app.algorithm_factory import KEM_ALGORITHM
from app.algorithm_strategy import KEM_WHITELIST, validate_algorithm
from app.db import db_cursor, unwrap_secret, wrap_secret
from app.schemas.kem import (
    KEMDecryptRequest,
    KEMDecryptResponse,
    KemEncryptRequest,
    KemEncryptResponse,
    KemInitResponse,
)

router = APIRouter()


def _resolve_kem_algorithm(header_value: Optional[str]) -> str:
    """X-Kem-Algorithm-Id 헤더 → 화이트리스트 검증 후 알고리즘 ID 반환.
    헤더 없으면 서버 기본값(KEM_ALGORITHM) 사용.
    헤더에 허용되지 않은 값 → 400 (startup fail-fast와 달리 요청 거부).
    """
    if not header_value:
        return KEM_ALGORITHM
    normalized = header_value.strip().upper()
    if normalized not in KEM_WHITELIST:
        raise HTTPException(
            status_code=400,
            detail=f"X-Kem-Algorithm-Id='{header_value}' is not allowed. "
                   f"Supported: {list(KEM_WHITELIST.keys())}",
        )
    return normalized


def _derive_aes_key(shared_secret: bytes) -> bytes:
    """HKDF (RFC 5869) — shared_secret → 32-byte AES key.
    info=b"pqc-kem-aes-v1" 로 도메인 분리(Domain Separation) 보장.
    """
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=b"pqc-kem-aes-v1",
    )
    return hkdf.derive(shared_secret)


_VALID_RISK_LEVELS = frozenset({"HIGH", "MEDIUM", "LOW", "NONE"})


def _cbom_insert(cur, algorithm_id: str, event: str, key_id: int, risk_level: str = "NONE") -> None:
    """cbom_assets 삽입 (Transactional — 실패 시 외부 트랜잭션 롤백).
    risk_level: X-Risk-Level 헤더 값 (Day 9 SNDL 라우팅) — 허용 목록 외 값은 'NONE' 처리.
    """
    safe_risk = risk_level.strip().upper() if risk_level else "NONE"
    if safe_risk not in _VALID_RISK_LEVELS:
        safe_risk = "NONE"
    cur.execute(
        """
        INSERT INTO cbom_assets (algorithm_id, asset, source, risk_level)
        VALUES (%s, %s, 'auto', %s)
        """,
        (algorithm_id, json.dumps({"event": event, "key_id": key_id}), safe_risk),
    )


@router.post("/keygen", status_code=410)
def kem_keygen():
    """Deprecated: /kem/init 을 사용하세요."""
    raise HTTPException(status_code=410, detail="Deprecated. Use POST /kem/init instead.")


@router.post("/init", response_model=KemInitResponse)
def kem_init(x_kem_algorithm_id: Optional[str] = Header(default=None)):
    """서버사이드 KEM 키쌍 생성 및 DB 저장.
    X-Kem-Algorithm-Id 헤더로 per-request 알고리즘 선택 가능 (기본: 서버 환경변수).
    secret_key는 AES-256-GCM 래핑 후 보관되며 외부에 반환되지 않는다.
    """
    algorithm = _resolve_kem_algorithm(x_kem_algorithm_id)

    with oqs.KeyEncapsulation(algorithm) as kem:
        public_key = kem.generate_keypair()
        secret_key = kem.export_secret_key()

    wrapped, iv = wrap_secret(bytes(secret_key))

    try:
        with db_cursor() as (conn, cur):
            cur.execute(
                """
                INSERT INTO kem_keys (algorithm_id, public_key, wrapped_secret, wrap_iv)
                VALUES (%s, %s, %s, %s)
                RETURNING id
                """,
                (algorithm, bytes(public_key), wrapped, iv),
            )
            key_id = cur.fetchone()[0]
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    return KemInitResponse(key_id=key_id, algorithm=algorithm)


@router.post("/encrypt", response_model=KemEncryptResponse)
def kem_encrypt(
    req: KemEncryptRequest,
    x_kem_algorithm_id: Optional[str] = Header(default=None),
    x_risk_level: Optional[str] = Header(default=None),
):
    """하이브리드 암호화: DB 공개키 조회 → KEM encap → HKDF → AES-256-GCM(plaintext).
    알고리즘은 DB의 key algorithm_id 기준 (kem_decrypt와 대칭).
    X-Kem-Algorithm-Id 헤더가 있고 DB algorithm_id와 불일치하면 400 반환.
    CBOM 로깅 포함 (Transactional — 로깅 실패 시 503).
    DB 읽기 → 암호화 → CBOM INSERT 순으로 db_cursor 밖에서 크립토 수행.
    """
    try:
        plaintext_bytes = base64.b64decode(req.plaintext)
    except Exception:
        raise HTTPException(status_code=400, detail="plaintext는 base64-encoded 이어야 합니다")

    # 1단계: DB 읽기 — public_key + algorithm_id 함께 조회
    try:
        with db_cursor() as (conn, cur):
            cur.execute(
                "SELECT public_key, algorithm_id FROM kem_keys WHERE id = %s",
                (req.key_id,),
            )
            row = cur.fetchone()
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    if row is None:
        raise HTTPException(status_code=404, detail="key_id not found")

    public_key_bytes, stored_algorithm = bytes(row[0]), row[1]

    # 헤더 있으면 DB algorithm_id와 일치 여부 검증 — 불일치 시 명시적 400
    if x_kem_algorithm_id:
        requested = x_kem_algorithm_id.strip().upper()
        if requested not in KEM_WHITELIST:
            raise HTTPException(status_code=400, detail="알고리즘 불일치: 허용되지 않는 알고리즘입니다")
        if requested != stored_algorithm:
            raise HTTPException(status_code=400, detail="알고리즘 불일치: 요청 알고리즘이 키 알고리즘과 다릅니다")

    # 2단계: KEM encap + AES 암호화 — stored_algorithm 기준 (db_cursor 외부)
    try:
        with oqs.KeyEncapsulation(stored_algorithm) as kem:
            kem_ciphertext, shared_secret = kem.encap_secret(public_key_bytes)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="KEM 캡슐화 오류") from exc

    aes_key = _derive_aes_key(bytes(shared_secret))
    aes_iv = os.urandom(12)
    aes_ciphertext = AESGCM(aes_key).encrypt(aes_iv, plaintext_bytes, None)

    # 3단계: CBOM INSERT (Transactional — 실패 시 503) — X-Risk-Level 헤더로 risk_level 기록
    try:
        with db_cursor() as (conn, cur):
            _cbom_insert(cur, stored_algorithm, "encrypt", req.key_id, risk_level=x_risk_level or "NONE")
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    return KemEncryptResponse(
        algorithm=stored_algorithm,
        kem_ciphertext=base64.b64encode(bytes(kem_ciphertext)).decode(),
        aes_ciphertext=base64.b64encode(aes_ciphertext).decode(),
        aes_iv=base64.b64encode(aes_iv).decode(),
    )


@router.post("/decrypt", response_model=KEMDecryptResponse)
def kem_decrypt(req: KEMDecryptRequest):
    """서버사이드 복호화: DB unwrap → KEM decap → HKDF → AES-256-GCM decrypt.
    Oracle 방어: KEM/AES/unwrap 실패는 동일 메시지 "복호화 오류" 반환.
    DB 읽기 → 복호화 → CBOM INSERT 순으로 db_cursor 밖에서 크립토 수행.
    """
    try:
        kem_ct_bytes = base64.b64decode(req.kem_ciphertext)
        aes_ct_bytes = base64.b64decode(req.aes_ciphertext)
        aes_iv_bytes = base64.b64decode(req.aes_iv)
    except Exception:
        raise HTTPException(status_code=400, detail="입력값은 base64-encoded 이어야 합니다")

    # 1단계: DB 읽기 — algorithm_id도 함께 조회 (키 생성 시 사용된 알고리즘으로 decap)
    try:
        with db_cursor() as (conn, cur):
            cur.execute(
                "SELECT wrapped_secret, wrap_iv, algorithm_id FROM kem_keys WHERE id = %s",
                (req.key_id,),
            )
            row = cur.fetchone()
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    if row is None:
        raise HTTPException(status_code=404, detail="key_id not found")

    wrapped_secret, wrap_iv, stored_algorithm = bytes(row[0]), bytes(row[1]), row[2]

    # 2단계: 복호화 (db_cursor 외부, Oracle 방어 — 단일 에러 메시지)
    try:
        secret_key_bytes = unwrap_secret(wrapped_secret, wrap_iv)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="복호화 오류") from exc

    try:
        with oqs.KeyEncapsulation(stored_algorithm, secret_key=secret_key_bytes) as kem:
            shared_secret = kem.decap_secret(kem_ct_bytes)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="복호화 오류") from exc

    aes_key = _derive_aes_key(bytes(shared_secret))
    try:
        plaintext_bytes = AESGCM(aes_key).decrypt(aes_iv_bytes, aes_ct_bytes, None)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="복호화 오류") from exc

    # 3단계: CBOM INSERT (Transactional — 실패 시 503)
    try:
        with db_cursor() as (conn, cur):
            _cbom_insert(cur, stored_algorithm, "decrypt", req.key_id)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    return KEMDecryptResponse(plaintext=base64.b64encode(plaintext_bytes).decode())
