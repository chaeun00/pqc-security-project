-- ============================================================
-- Step 1-C: PQC Security DB 초기 스키마
--   001_schema.sql
--   - users / sessions (정규화)
--   - key_metadata / cbom_assets (JSONB + GIN 인덱스)
-- ============================================================

-- ── users ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL    PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── sessions ───────────────────────────────────────────────
-- algorithm_id: 암호 민첩성 이력 추적 (어떤 PQC 알고리즘으로 세션 생성됐는지)
CREATE TABLE IF NOT EXISTS sessions (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    algorithm_id VARCHAR(64)  NOT NULL,
    token_hash   BYTEA        NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── key_metadata ───────────────────────────────────────────
-- payload: 키 메타데이터 (알고리즘 파라미터, 공개키 핑거프린트 등)
CREATE TABLE IF NOT EXISTS key_metadata (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    algorithm_id VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── cbom_assets ────────────────────────────────────────────
-- asset:      CBOM 항목 (컴포넌트 상세, 버전, 의존성 등)
-- source:     'auto'(자동 탐지) | 'manual'(수동 등록) — 하이브리드 CBOM
-- risk_level: 취약점 위험도
CREATE TABLE IF NOT EXISTS cbom_assets (
    id           BIGSERIAL    PRIMARY KEY,
    algorithm_id VARCHAR(64)  NOT NULL,
    asset        JSONB        NOT NULL DEFAULT '{}',
    source       VARCHAR(8)   NOT NULL DEFAULT 'auto'
                     CHECK (source IN ('auto', 'manual')),
    risk_level   VARCHAR(8)   NOT NULL DEFAULT 'NONE'
                     CHECK (risk_level IN ('HIGH', 'MEDIUM', 'LOW', 'NONE')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── GIN 인덱스 (JSONB 고속 조회) ──────────────────────────
CREATE INDEX IF NOT EXISTS idx_cbom_assets_asset
    ON cbom_assets USING GIN (asset);

CREATE INDEX IF NOT EXISTS idx_key_metadata_payload
    ON key_metadata USING GIN (payload);

-- ── B-Tree 인덱스 (FK / 범위 조회) ───────────────────────
-- sessions.user_id: 사용자별 세션 조회
CREATE INDEX IF NOT EXISTS idx_sessions_user_id
    ON sessions (user_id);

-- sessions.expires_at: 만료 세션 정리 (TTL 쿼리)
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at
    ON sessions (expires_at);

-- key_metadata.user_id: 사용자별 키 목록 조회
CREATE INDEX IF NOT EXISTS idx_key_metadata_user_id
    ON key_metadata (user_id);

-- key_metadata.algorithm_id: 암호 민첩성 — 알고리즘별 키 전환 조회
CREATE INDEX IF NOT EXISTS idx_key_metadata_algorithm_id
    ON key_metadata (algorithm_id);
