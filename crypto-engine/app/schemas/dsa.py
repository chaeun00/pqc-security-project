from pydantic import BaseModel


class DSASignRequest(BaseModel):
    message: str       # 서명할 평문


class DSASignResponse(BaseModel):
    algorithm: str
    message: str
    signature: str     # base64
    public_key: str    # base64
    secret_key: str    # base64


class DSAVerifyRequest(BaseModel):
    message: str       # 검증할 평문
    signature: str     # base64
    public_key: str    # base64


class DSAVerifyResponse(BaseModel):
    algorithm: str
    valid: bool
