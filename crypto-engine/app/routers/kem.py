import base64

import oqs
from fastapi import APIRouter, HTTPException

from app.algorithm_factory import KEM_ALGORITHM, get_kem
from app.schemas.kem import (
    KEMDecryptRequest,
    KEMDecryptResponse,
    KEMEncryptRequest,
    KEMEncryptResponse,
    KEMKeygenResponse,
)

router = APIRouter()


@router.post("/keygen", response_model=KEMKeygenResponse)
def kem_keygen():
    """KEM 키쌍 생성 (수신자)"""
    with get_kem() as kem:
        public_key = kem.generate_keypair()
        secret_key = kem.export_secret_key()

    return KEMKeygenResponse(
        algorithm=KEM_ALGORITHM,
        public_key=base64.b64encode(public_key).decode(),
        secret_key=base64.b64encode(secret_key).decode(),
    )


@router.post("/encrypt", response_model=KEMEncryptResponse)
def kem_encrypt(req: KEMEncryptRequest):
    """수신자 공개키로 공유 비밀 캡슐화 (송신자)"""
    try:
        public_key_bytes = base64.b64decode(req.public_key)
    except Exception:
        raise HTTPException(status_code=422, detail="base64 디코딩 실패")

    try:
        with get_kem() as kem:
            if len(public_key_bytes) != kem.details["length_public_key"]:
                raise ValueError("공개키 길이 불일치")
            ciphertext, shared_secret = kem.encap_secret(public_key_bytes)
    except Exception:
        raise HTTPException(status_code=400, detail="캡슐화 오류")

    return KEMEncryptResponse(
        algorithm=KEM_ALGORITHM,
        ciphertext=base64.b64encode(ciphertext).decode(),
        shared_secret=base64.b64encode(shared_secret).decode(),
    )


@router.post("/decrypt", response_model=KEMDecryptResponse)
def kem_decrypt(req: KEMDecryptRequest):
    """비밀키 + 암호문으로 공유 비밀 복원 (수신자)"""
    try:
        secret_key_bytes = base64.b64decode(req.secret_key)
        ciphertext_bytes = base64.b64decode(req.ciphertext)
    except Exception:
        raise HTTPException(status_code=422, detail="base64 디코딩 실패")

    try:
        with oqs.KeyEncapsulation(KEM_ALGORITHM, secret_key_bytes) as kem:
            shared_secret = kem.decap_secret(ciphertext_bytes)
    except Exception:
        raise HTTPException(status_code=400, detail="복호화 오류")

    return KEMDecryptResponse(
        algorithm=KEM_ALGORITHM,
        shared_secret=base64.b64encode(shared_secret).decode(),
    )
