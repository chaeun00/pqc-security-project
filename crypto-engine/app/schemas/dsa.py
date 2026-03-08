from pydantic import BaseModel, Field, field_validator


class DSASignRequest(BaseModel):
    # max_length=65536: 무제한 페이로드 → 메모리 소진 DoS 차단
    message: str = Field(max_length=65536)

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
    # max_length=65536: sign과 동일 제한 적용
    message: str = Field(max_length=65536)
    signature: str     # base64
    public_key: str    # base64


class DSAVerifyResponse(BaseModel):
    algorithm: str
    valid: bool
