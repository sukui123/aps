-- =====================================================================
-- B2 修复：集成任务幂等键唯一约束跨租户串扰
-- PostgreSQL 15 方言。Flyway validate-on-migrate=true，禁止改写 V1；只新增本迁移。
--
-- V1 中 uq_integration_idempotency 仅覆盖 (idempotency_key)，
-- 租户 B 复用租户 A 的键会命中 A 的任务（串单/信息泄露），
-- 并发复用同键还会触发未捕获的 DuplicateKeyException（90000/500）。
-- 改为租户内唯一：相同键在不同租户下各自独立，同租户并发由唯一键兜底。
-- =====================================================================

DROP INDEX IF EXISTS uq_integration_idempotency;

CREATE UNIQUE INDEX uq_integration_idempotency
    ON integration_task (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
