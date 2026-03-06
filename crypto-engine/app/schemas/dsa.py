from pydantic import BaseModel, field_validator


class DSASignRequest(BaseModel):
    message: str       # 서명할 평문

    @field_validator("message")
    @classmethod
    def message_not_empty(cls, v: str) -> str:
        if not v:
            raise ValueError("message는 빈 문자열일 수 없습니다")
        return v


class DSASignResponse(BaseModel):
    algorithm: str
    message: str
    signature: str     # base64
    public_key: str    # base64


class DSAVerifyRequest(BaseModel):
    message: str       # 검증할 평문
    signature: str     # base64
    public_key: str    # base64


class DSAVerifyResponse(BaseModel):
    algorithm: str
    valid: bool
