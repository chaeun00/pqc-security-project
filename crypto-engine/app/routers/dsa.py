import base64

import oqs
from fastapi import APIRouter, HTTPException

from app.algorithm_factory import DSA_ALGORITHM
from app.schemas.dsa import (
    DSASignRequest,
    DSASignResponse,
    DSAVerifyRequest,
    DSAVerifyResponse,
)

router = APIRouter()


@router.post("/sign", response_model=DSASignResponse)
def dsa_sign(req: DSASignRequest):
    """메시지 서명 (키쌍 생성 포함)"""
    with oqs.Signature(DSA_ALGORITHM) as sig:
        public_key = sig.generate_keypair()
        secret_key = sig.export_secret_key()
        signature = sig.sign(req.message.encode())

    return DSASignResponse(
        algorithm=DSA_ALGORITHM,
        message=req.message,
        signature=base64.b64encode(signature).decode(),
        public_key=base64.b64encode(public_key).decode(),
        secret_key=base64.b64encode(secret_key).decode(),
    )


@router.post("/verify", response_model=DSAVerifyResponse)
def dsa_verify(req: DSAVerifyRequest):
    """서명 검증"""
    try:
        signature_bytes = base64.b64decode(req.signature)
        public_key_bytes = base64.b64decode(req.public_key)
    except Exception:
        raise HTTPException(status_code=422, detail="base64 디코딩 실패")

    try:
        with oqs.Signature(DSA_ALGORITHM) as sig:
            valid = sig.verify(req.message.encode(), signature_bytes, public_key_bytes)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

    return DSAVerifyResponse(algorithm=DSA_ALGORITHM, valid=valid)
