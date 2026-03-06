from pydantic import BaseModel


class KEMKeygenResponse(BaseModel):
    algorithm: str
    public_key: str    # base64 (캡슐화 주체에게 전달)
    secret_key: str    # base64 (수신자 보관)


class KEMEncryptRequest(BaseModel):
    public_key: str    # base64 (수신자 공개키)


class KEMEncryptResponse(BaseModel):
    algorithm: str
    ciphertext: str    # base64
    shared_secret: str # base64


class KEMDecryptRequest(BaseModel):
    secret_key: str    # base64
    ciphertext: str    # base64


class KEMDecryptResponse(BaseModel):
    algorithm: str
    shared_secret: str # base64
