from pydantic import BaseModel


class KEMEncryptResponse(BaseModel):
    algorithm: str
    public_key: str    # base64
    secret_key: str    # base64
    ciphertext: str    # base64
    shared_secret: str # base64


class KEMDecryptRequest(BaseModel):
    secret_key: str    # base64
    ciphertext: str    # base64


class KEMDecryptResponse(BaseModel):
    algorithm: str
    shared_secret: str # base64
