import base64

import oqs
from fastapi import APIRouter, HTTPException

from app.algorithm_factory import KEM_ALGORITHM
from app.schemas.kem import (
    KEMDecryptRequest,
    KEMDecryptResponse,
    KEMEncryptResponse,
)

router = APIRouter()


@router.post("/encrypt", response_model=KEMEncryptResponse)
def kem_encrypt():
    """KEM 키쌍 생성 + 공유 비밀 캡슐화 (Encapsulate)"""
    with oqs.KeyEncapsulation(KEM_ALGORITHM) as kem:
        public_key = kem.generate_keypair()
        secret_key = kem.export_secret_key()
        ciphertext, shared_secret = kem.encap_secret(public_key)

    return KEMEncryptResponse(
        algorithm=KEM_ALGORITHM,
        public_key=base64.b64encode(public_key).decode(),
        secret_key=base64.b64encode(secret_key).decode(),
        ciphertext=base64.b64encode(ciphertext).decode(),
        shared_secret=base64.b64encode(shared_secret).decode(),
    )


@router.post("/decrypt", response_model=KEMDecryptResponse)
def kem_decrypt(req: KEMDecryptRequest):
    """비밀키 + 암호문으로 공유 비밀 복원 (Decapsulate)"""
    try:
        secret_key_bytes = base64.b64decode(req.secret_key)
        ciphertext_bytes = base64.b64decode(req.ciphertext)
    except Exception:
        raise HTTPException(status_code=422, detail="base64 디코딩 실패")

    try:
        with oqs.KeyEncapsulation(KEM_ALGORITHM, secret_key_bytes) as kem:
            shared_secret = kem.decap_secret(ciphertext_bytes)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

    return KEMDecryptResponse(
        algorithm=KEM_ALGORITHM,
        shared_secret=base64.b64encode(shared_secret).decode(),
    )
