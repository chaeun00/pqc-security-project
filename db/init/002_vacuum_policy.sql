-- ============================================================
-- Step 1-C: VACUUM 파라미터 분리
--   002_vacuum_policy.sql
--   - cbom_assets: JSONB 갱신 빈도 높음 → 공격적 autovacuum
--   - key_metadata: 읽기 위주, INSERT-only → 보수적 autovacuum
-- ============================================================

-- ── cbom_assets: 잦은 UPDATE → 빠른 dead tuple 정리 ───────
ALTER TABLE cbom_assets SET (
    autovacuum_vacuum_scale_factor     = 0.01,
    autovacuum_analyze_scale_factor    = 0.005,
    autovacuum_vacuum_cost_delay       = 2
);

-- ── key_metadata: INSERT-only, 읽기 위주 → 보수적 정리 ────
ALTER TABLE key_metadata SET (
    autovacuum_vacuum_scale_factor     = 0.1,
    autovacuum_analyze_scale_factor    = 0.05,
    autovacuum_vacuum_cost_delay       = 20
);
