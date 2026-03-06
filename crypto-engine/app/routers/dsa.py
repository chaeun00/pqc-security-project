import base64

import oqs
from fastapi import APIRouter, HTTPException

from app.algorithm_factory import DSA_ALGORITHM, DSA_PUBLIC_KEY, get_signature
from app.schemas.dsa import (
    DSASignRequest,
    DSASignResponse,
    DSAVerifyRequest,
    DSAVerifyResponse,
)

router = APIRouter()


@router.post("/sign", response_model=DSASignResponse)
def dsa_sign(req: DSASignRequest):
    """메시지 서명 (persistent keypair 사용)"""
    with get_signature() as sig:
        signature = sig.sign(req.message.encode())

    return DSASignResponse(
        algorithm=DSA_ALGORITHM,
        message=req.message,
        signature=base64.b64encode(signature).decode(),
        public_key=base64.b64encode(DSA_PUBLIC_KEY).decode(),
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
    except Exception:
        raise HTTPException(status_code=400, detail="서명 검증 오류")

    return DSAVerifyResponse(algorithm=DSA_ALGORITHM, valid=valid)
