from pydantic import BaseModel


# ── Deprecated (Day 6: /keygen → 410 Gone) ──────────────────
class KEMKeygenResponse(BaseModel):
    algorithm: str
    public_key: str
    secret_key: str


# ── Day 6: 서버사이드 키 관리 ──────────────────────────────
class KemInitResponse(BaseModel):
    key_id: int        # DB kem_keys.id
    algorithm: str


# ── Day 7: 하이브리드 암호화 (KEM + AES-256-GCM) ────────────
class KemEncryptRequest(BaseModel):
    key_id: int        # /kem/init에서 발급받은 키 ID
    plaintext: str     # base64 (암호화할 평문)


class KemEncryptResponse(BaseModel):
    algorithm: str
    kem_ciphertext: str   # base64 (KEM 캡슐화 ciphertext)
    aes_ciphertext: str   # base64 (AES-256-GCM 암호문)
    aes_iv: str           # base64 (AES-256-GCM nonce)


# ── Day 7: 서버사이드 복호화 ──────────────────────────────
class KEMDecryptRequest(BaseModel):
    key_id: int           # DB kem_keys.id
    kem_ciphertext: str   # base64
    aes_ciphertext: str   # base64
    aes_iv: str           # base64


class KEMDecryptResponse(BaseModel):
    plaintext: str        # base64 (복호화된 평문)
