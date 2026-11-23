-- =====================================================================
-- B3 修复：禁用账号/改密后旧 JWT 仍长期有效
-- PostgreSQL 15 方言；只新增列，不改写 V1/V2/V3。
--
-- token_version 随 JWT 的 ver claim 下发；鉴权链路每次比对库中当前版本。
-- 禁用账号或改密时递增版本，使该用户此前签发的所有 token 立即失效，
-- 无需记录/遍历已签发 jti，也不依赖 10 分钟缓存自然过期。
-- =====================================================================

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_sys_user_token_version ON sys_user (tenant_id, id, token_version);
