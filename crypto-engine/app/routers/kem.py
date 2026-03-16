import base64

import oqs
from fastapi import APIRouter, HTTPException

from app.algorithm_factory import KEM_ALGORITHM, get_kem
from app.db import db_cursor, wrap_secret
from app.schemas.kem import (
    KEMDecryptRequest,
    KEMDecryptResponse,
    KemEncryptRequest,
    KemEncryptResponse,
    KemInitResponse,
)

router = APIRouter()


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
    """DB에서 공개키 조회 후 캡슐화.
    ciphertext만 반환. shared_secret은 서버 내부에서만 사용(Day 7에서 확장).
    """
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

    try:
        with get_kem() as kem:
            ciphertext, _ = kem.encap_secret(public_key_bytes)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="캡슐화 오류") from exc

    return KemEncryptResponse(
        algorithm=KEM_ALGORITHM,
        ciphertext=base64.b64encode(ciphertext).decode(),
    )


@router.post("/decrypt", status_code=410)
def kem_decrypt(req: KEMDecryptRequest):
    """Deprecated: 외부 secret_key 수신은 decryption oracle 취약점. Day 7 서버사이드 재설계 예정."""
    raise HTTPException(
        status_code=410,
        detail="Deprecated. Server-side decryption will be added in a future release.",
    )
