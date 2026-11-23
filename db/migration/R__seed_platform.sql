-- =====================================================================
-- APS 平台底座开发/沙箱引导数据（Flyway repeatable，全部幂等）
-- 仅用于本地与测试沙箱：默认开通 admin/viewer 两个开发账号与两个演示租户。
-- 生产环境不得沿用本文件中的账号与口令；上线前须由运维禁用或改密。
-- =====================================================================

-- 租户（DEFAULT 为权限码种子的权威租户；TENANT_B 用于越权/RLS 验证）
INSERT INTO tenant (code, name, enabled)
VALUES ('DEFAULT', '默认租户', TRUE), ('TENANT_B', '租户B-越权验证', TRUE)
ON CONFLICT (code) DO NOTHING;

-- 权限码字典（PF-02）
INSERT INTO sys_permission (tenant_id, code, name, resource)
SELECT t.id, p.code, p.name, p.resource
  FROM tenant t
  CROSS JOIN (VALUES
      ('user:manage',        '用户管理',   '/users'),
      ('role:manage',        '角色管理',   '/roles'),
      ('audit:read',         '审计查询',   '/audit'),
      ('audit:export',       '审计导出',   '/audit/export'),
      ('integration:manage', '集成配置管理', '/integrations'),
      ('integration:read',   '任务监控',   '/integrations/tasks'),
      ('order:sync',         '订单同步',   '/orders/sync')
  ) AS p(code, name, resource)
 WHERE t.code IN ('DEFAULT', 'TENANT_B')
ON CONFLICT (tenant_id, code) DO NOTHING;

-- 角色：ADMIN 拥有全部平台权限；VIEWER 无任何权限（用于 403 验证）
INSERT INTO sys_role (tenant_id, code, name)
SELECT t.id, r.code, r.name
  FROM tenant t
  CROSS JOIN (VALUES ('ADMIN', '系统管理员'), ('VIEWER', '只读用户')) AS r(code, name)
 WHERE t.code IN ('DEFAULT', 'TENANT_B')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
  FROM sys_role r
  JOIN sys_permission p ON p.tenant_id = r.tenant_id
 WHERE r.code = 'ADMIN'
ON CONFLICT (tenant_id, role_id, permission_id) DO NOTHING;

-- 开发账号（BCrypt 哈希，明文仅在此注释中声明一次：admin / viewer 均为 Admin@12345）
INSERT INTO sys_user (tenant_id, username, password_hash, display_name, status)
SELECT t.id, u.username,
       '$2a$10$rM/MMjll7KESnlPT2eKwoe4xGEccv.bWhWHnaQH1xxFck/xyHceFu',
       u.display_name, 'ACTIVE'
  FROM tenant t
  CROSS JOIN (VALUES ('admin', '系统管理员'), ('viewer', '只读用户')) AS u(username, display_name)
 WHERE t.code = 'DEFAULT'
ON CONFLICT (tenant_id, username) DO NOTHING;

INSERT INTO sys_user (tenant_id, username, password_hash, display_name, status)
SELECT t.id, 'admin_b',
       '$2a$10$rM/MMjll7KESnlPT2eKwoe4xGEccv.bWhWHnaQH1xxFck/xyHceFu',
       '租户B管理员', 'ACTIVE'
  FROM tenant t
 WHERE t.code = 'TENANT_B'
ON CONFLICT (tenant_id, username) DO NOTHING;

INSERT INTO sys_user_role (tenant_id, user_id, role_id)
SELECT u.tenant_id, u.id, r.id
  FROM sys_user u
  JOIN sys_role r ON r.tenant_id = u.tenant_id AND r.code = 'ADMIN'
 WHERE u.username IN ('admin', 'admin_b')
ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING;

INSERT INTO sys_user_role (tenant_id, user_id, role_id)
SELECT u.tenant_id, u.id, r.id
  FROM sys_user u
  JOIN sys_role r ON r.tenant_id = u.tenant_id AND r.code = 'VIEWER'
 WHERE u.username = 'viewer'
ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING;

-- 跨租户可见性验证锚点：TENANT_B 的一条审计记录，DEFAULT 租户不应看到
INSERT INTO audit_log (tenant_id, actor, action, target_type, target_id,
                       before_json, after_json, trace_id, created_at)
SELECT t.id, 'seed', 'SEED_TENANT_MARKER', 'Tenant', 'TENANT_B',
       '{}'::jsonb, '{}'::jsonb, 'seed-trace-b', now()
  FROM tenant t
 WHERE t.code = 'TENANT_B'
   AND NOT EXISTS (SELECT 1 FROM audit_log a
                    WHERE a.tenant_id = t.id AND a.action = 'SEED_TENANT_MARKER');

-- 数据源配置示例：DB_VIEW 为可运行兜底；FILE 默认停用（需外部放置样本文件）
INSERT INTO integration_config (tenant_id, code, name, type, endpoint, signature_type,
                                ip_allowlist, enabled)
SELECT t.id, 'DB_VIEW_DEV', '订单视图（开发）', 'DB_VIEW', 'v_integration_order_sample',
       'NONE', '[]'::jsonb, TRUE
  FROM tenant t
 WHERE t.code = 'DEFAULT'
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO integration_config (tenant_id, code, name, type, endpoint, signature_type,
                                ip_allowlist, enabled)
SELECT t.id, 'FILE_DEV', '订单文件（开发）', 'FILE', 'integration/orders-sample.json',
       'NONE', '[]'::jsonb, FALSE
  FROM tenant t
 WHERE t.code = 'DEFAULT'
ON CONFLICT (tenant_id, code) DO NOTHING;
