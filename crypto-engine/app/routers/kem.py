import base64
import json
import os

import oqs
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from fastapi import APIRouter, HTTPException

from app.algorithm_factory import KEM_ALGORITHM, get_kem
from app.db import db_cursor, unwrap_secret, wrap_secret
from app.schemas.kem import (
    KEMDecryptRequest,
    KEMDecryptResponse,
    KemEncryptRequest,
    KemEncryptResponse,
    KemInitResponse,
)

router = APIRouter()


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


def _cbom_insert(cur, algorithm_id: str, event: str, key_id: int) -> None:
    """cbom_assets 삽입 (Transactional — 실패 시 외부 트랜잭션 롤백)."""
    cur.execute(
        """
        INSERT INTO cbom_assets (algorithm_id, asset, source, risk_level)
        VALUES (%s, %s, 'auto', 'NONE')
        """,
        (algorithm_id, json.dumps({"event": event, "key_id": key_id})),
    )


@router.post("/keygen", status_code=410)
def kem_keygen():
    """Deprecated: /kem/init 을 사용하세요."""
    raise HTTPException(status_code=410, detail="Deprecated. Use POST /kem/init instead.")


@router.post("/init", response_model=KemInitResponse)
def kem_init():
    """서버사이드 KEM 키쌍 생성 및 DB 저장.
    secret_key는 AES-256-GCM 래핑 후 보관되며 외부에 반환되지 않는다.
    """
    with get_kem() as kem:
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
                (KEM_ALGORITHM, bytes(public_key), wrapped, iv),
            )
            key_id = cur.fetchone()[0]
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    return KemInitResponse(key_id=key_id, algorithm=KEM_ALGORITHM)


@router.post("/encrypt", response_model=KemEncryptResponse)
def kem_encrypt(req: KemEncryptRequest):
    """하이브리드 암호화: DB 공개키 조회 → KEM encap → HKDF → AES-256-GCM(plaintext).
    CBOM 로깅 포함 (Transactional — 로깅 실패 시 503).
    DB 읽기 → 암호화 → CBOM INSERT 순으로 db_cursor 밖에서 크립토 수행.
    """
    try:
        plaintext_bytes = base64.b64decode(req.plaintext)
    except Exception:
        raise HTTPException(status_code=400, detail="plaintext는 base64-encoded 이어야 합니다")

    # 1단계: DB 읽기 (read-only, db_cursor 내 HTTPException 없음)
    try:
        with db_cursor() as (conn, cur):
            cur.execute(
                "SELECT public_key FROM kem_keys WHERE id = %s",
                (req.key_id,),
            )
            row = cur.fetchone()
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    if row is None:
        raise HTTPException(status_code=404, detail="key_id not found")

    public_key_bytes = bytes(row[0])

    # 2단계: KEM encap + AES 암호화 (db_cursor 외부)
    try:
        with get_kem() as kem:
            kem_ciphertext, shared_secret = kem.encap_secret(public_key_bytes)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="KEM 캡슐화 오류") from exc

    aes_key = _derive_aes_key(bytes(shared_secret))
    aes_iv = os.urandom(12)
    aes_ciphertext = AESGCM(aes_key).encrypt(aes_iv, plaintext_bytes, None)

    # 3단계: CBOM INSERT (Transactional — 실패 시 503)
    try:
        with db_cursor() as (conn, cur):
            _cbom_insert(cur, KEM_ALGORITHM, "encrypt", req.key_id)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    return KemEncryptResponse(
        algorithm=KEM_ALGORITHM,
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

    # 1단계: DB 읽기 (read-only, db_cursor 내 HTTPException 없음)
    try:
        with db_cursor() as (conn, cur):
            cur.execute(
                "SELECT wrapped_secret, wrap_iv FROM kem_keys WHERE id = %s",
                (req.key_id,),
            )
            row = cur.fetchone()
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    if row is None:
        raise HTTPException(status_code=404, detail="key_id not found")

    wrapped_secret, wrap_iv = bytes(row[0]), bytes(row[1])

    # 2단계: 복호화 (db_cursor 외부, Oracle 방어 — 단일 에러 메시지)
    try:
        secret_key_bytes = unwrap_secret(wrapped_secret, wrap_iv)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="복호화 오류") from exc

    try:
        with oqs.KeyEncapsulation(KEM_ALGORITHM, secret_key=secret_key_bytes) as kem:
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
            _cbom_insert(cur, KEM_ALGORITHM, "decrypt", req.key_id)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"DB 오류: {exc}") from exc

    return KEMDecryptResponse(plaintext=base64.b64encode(plaintext_bytes).decode())
