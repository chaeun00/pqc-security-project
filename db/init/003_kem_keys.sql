-- ============================================================
-- Day 6: KEM 서버사이드 키 관리 스키마
--   003_kem_keys.sql
--   - kem_keys: 공개키 + AES-256-GCM 래핑된 비밀키 보관
-- ============================================================

CREATE TABLE IF NOT EXISTS kem_keys (
    id              BIGSERIAL    PRIMARY KEY,
    algorithm_id    VARCHAR(64)  NOT NULL,
    public_key      BYTEA        NOT NULL,
    wrapped_secret  BYTEA        NOT NULL,   -- AES-256-GCM(secret_key): ciphertext||tag
    wrap_iv         BYTEA        NOT NULL,   -- GCM nonce (12 bytes)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kem_keys_created_at
    ON kem_keys (created_at);
