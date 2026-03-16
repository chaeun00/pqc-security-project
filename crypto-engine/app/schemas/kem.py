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


class KemEncryptRequest(BaseModel):
    key_id: int        # /kem/init에서 발급받은 키 ID


class KemEncryptResponse(BaseModel):
    algorithm: str
    ciphertext: str    # base64 (KEM ciphertext; shared_secret은 서버 미반환)


# ── /decrypt: 기존 인터페이스 유지 (Day 7에서 재설계 예정) ──
class KEMDecryptRequest(BaseModel):
    secret_key: str    # base64
    ciphertext: str    # base64


class KEMDecryptResponse(BaseModel):
    algorithm: str
    shared_secret: str # base64
