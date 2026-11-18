-- ============================================================================
-- APS 排产系统 · P0 基线库结构
-- 目标库：PostgreSQL 16
-- 迁移工具：Flyway（V1__p0_baseline.sql）
--
-- 覆盖范围
--   主数据 / 订单 / 排产 / 未排原因 / 发布审批与下达 / 用户权限 / 审计
-- 说明
--   1. 所有时间使用 timestamptz，业务日历单独用 date/time 表达
--   2. plant_id 为行级数据权限与多工厂预留，P0 单厂填默认值
--   3. 状态字段用 varchar + CHECK，便于后续扩枚举而不锁死类型
--   4. 锁定 / 冻结相关字段保留但 P0 恒为 false（P1 启用）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 通用函数
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- 1. 组织与工厂
-- ---------------------------------------------------------------------------
CREATE TABLE md_plant (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    tz          VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 2. 日历与班次
-- ---------------------------------------------------------------------------
CREATE TABLE md_calendar (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT       NOT NULL REFERENCES md_plant(id),
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    tz          VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    UNIQUE (plant_id, code)
);

CREATE TABLE md_shift (
    id                BIGSERIAL PRIMARY KEY,
    calendar_id       BIGINT      NOT NULL REFERENCES md_calendar(id) ON DELETE CASCADE,
    shift_no          SMALLINT    NOT NULL,
    start_offset_min  INT         NOT NULL CHECK (start_offset_min >= 0 AND start_offset_min < 1440),
    end_offset_min    INT         NOT NULL CHECK (end_offset_min > 0 AND end_offset_min <= 2880),
    cross_midnight    BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (calendar_id, shift_no),
    CHECK (end_offset_min > start_offset_min)
);

CREATE TABLE md_calendar_day (
    calendar_id BIGINT  NOT NULL REFERENCES md_calendar(id) ON DELETE CASCADE,
    work_date   DATE    NOT NULL,
    is_workday  BOOLEAN NOT NULL DEFAULT TRUE,
    note        VARCHAR(255),
    PRIMARY KEY (calendar_id, work_date)
);

-- 例外：加班、半天班、临时开线、设备保养
CREATE TABLE md_calendar_exception (
    id           BIGSERIAL PRIMARY KEY,
    calendar_id  BIGINT      NOT NULL REFERENCES md_calendar(id) ON DELETE CASCADE,
    work_date    DATE        NOT NULL,
    shift_no     SMALLINT,
    start_ts     TIMESTAMPTZ NOT NULL,
    end_ts       TIMESTAMPTZ NOT NULL,
    ex_type      VARCHAR(16) NOT NULL CHECK (ex_type IN ('OT','HALF','EXTRA','MAINT','HOLIDAY')),
    resource_id  BIGINT,
    note         VARCHAR(255),
    CHECK (end_ts > start_ts)
);
CREATE INDEX idx_cal_ex_date ON md_calendar_exception(calendar_id, work_date);

-- ---------------------------------------------------------------------------
-- 3. 资源
-- ---------------------------------------------------------------------------
CREATE TABLE md_resource (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT       NOT NULL REFERENCES md_plant(id),
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    res_type    VARCHAR(32)  NOT NULL
                CHECK (res_type IN ('PLANT','WORKSHOP','WORKCENTER','MACHINE','LINE','TOOL','CREW')),
    parent_id   BIGINT       REFERENCES md_resource(id),
    calendar_id BIGINT       REFERENCES md_calendar(id),
    capacity    NUMERIC(8,2) NOT NULL DEFAULT 1 CHECK (capacity > 0),
    efficiency  NUMERIC(5,3) NOT NULL DEFAULT 1 CHECK (efficiency > 0),
    workshop_id BIGINT,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE','INACTIVE','MAINT')),
    attrs       JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (plant_id, code)
);
CREATE INDEX idx_resource_parent ON md_resource(parent_id);
CREATE INDEX idx_resource_type   ON md_resource(plant_id, res_type, status);

CREATE TABLE md_resource_capability (
    resource_id BIGINT      NOT NULL REFERENCES md_resource(id) ON DELETE CASCADE,
    capability  VARCHAR(64) NOT NULL,
    level       SMALLINT    NOT NULL DEFAULT 1,
    PRIMARY KEY (resource_id, capability)
);
CREATE INDEX idx_cap_capability ON md_resource_capability(capability);

CREATE TABLE md_downtime (
    id          BIGSERIAL PRIMARY KEY,
    resource_id BIGINT      NOT NULL REFERENCES md_resource(id) ON DELETE CASCADE,
    start_ts    TIMESTAMPTZ NOT NULL,
    end_ts      TIMESTAMPTZ NOT NULL,
    reason      VARCHAR(64),
    source      VARCHAR(16) NOT NULL DEFAULT 'PLANNED' CHECK (source IN ('PLANNED','MES')),
    CHECK (end_ts > start_ts)
);
CREATE INDEX idx_downtime_res ON md_downtime(resource_id, start_ts);

CREATE TABLE md_setup_matrix (
    resource_id BIGINT      NOT NULL REFERENCES md_resource(id) ON DELETE CASCADE,
    from_family VARCHAR(64) NOT NULL,
    to_family   VARCHAR(64) NOT NULL,
    setup_min   INT         NOT NULL CHECK (setup_min >= 0),
    PRIMARY KEY (resource_id, from_family, to_family)
);

-- 替代资源：某工序可用的其他资源（P0 用能力标签匹配，此表为精确指定）
CREATE TABLE md_alt_resource (
    operation_id BIGINT   NOT NULL,
    resource_id  BIGINT   NOT NULL REFERENCES md_resource(id) ON DELETE CASCADE,
    priority     SMALLINT NOT NULL DEFAULT 1,
    PRIMARY KEY (operation_id, resource_id)
);

-- ---------------------------------------------------------------------------
-- 4. 物料与工艺
-- ---------------------------------------------------------------------------
CREATE TABLE md_item (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT       NOT NULL REFERENCES md_plant(id),
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    uom         VARCHAR(16)  NOT NULL DEFAULT 'EA',
    family      VARCHAR(64),
    lot_size    INT,
    min_batch   INT          NOT NULL DEFAULT 1,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    UNIQUE (plant_id, code)
);
CREATE INDEX idx_item_family ON md_item(plant_id, family);

CREATE TABLE md_bom (
    id             BIGSERIAL PRIMARY KEY,
    parent_item_id BIGINT      NOT NULL REFERENCES md_item(id),
    version        VARCHAR(16) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    valid_from     DATE,
    valid_to       DATE,
    UNIQUE (parent_item_id, version)
);

CREATE TABLE md_bom_line (
    id                BIGSERIAL PRIMARY KEY,
    bom_id            BIGINT        NOT NULL REFERENCES md_bom(id) ON DELETE CASCADE,
    component_item_id BIGINT        NOT NULL REFERENCES md_item(id),
    qty_per           NUMERIC(14,4) NOT NULL CHECK (qty_per > 0),
    scrap_rate        NUMERIC(5,4)  NOT NULL DEFAULT 0,
    at_op_seq         INT,
    alt_group         VARCHAR(32),
    alt_priority      SMALLINT      NOT NULL DEFAULT 1
);
CREATE INDEX idx_bom_line_bom  ON md_bom_line(bom_id);
CREATE INDEX idx_bom_line_item ON md_bom_line(component_item_id);

CREATE TABLE md_routing (
    id         BIGSERIAL PRIMARY KEY,
    plant_id   BIGINT      NOT NULL REFERENCES md_plant(id),
    code       VARCHAR(64) NOT NULL,
    item_id    BIGINT      NOT NULL REFERENCES md_item(id),
    version    VARCHAR(16) NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
               CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    valid_from DATE,
    valid_to   DATE,
    UNIQUE (plant_id, code, version)
);
CREATE INDEX idx_routing_item ON md_routing(item_id, status);

CREATE TABLE md_routing_alt (
    routing_id     BIGINT   NOT NULL REFERENCES md_routing(id) ON DELETE CASCADE,
    alt_routing_id BIGINT   NOT NULL REFERENCES md_routing(id) ON DELETE CASCADE,
    priority       SMALLINT NOT NULL DEFAULT 1,
    PRIMARY KEY (routing_id, alt_routing_id),
    CHECK (routing_id <> alt_routing_id)
);

CREATE TABLE md_operation (
    id             BIGSERIAL PRIMARY KEY,
    routing_id     BIGINT        NOT NULL REFERENCES md_routing(id) ON DELETE CASCADE,
    seq            INT           NOT NULL,
    code           VARCHAR(32)   NOT NULL,
    name           VARCHAR(128)  NOT NULL,
    std_time_min   NUMERIC(10,2) NOT NULL CHECK (std_time_min > 0),
    setup_time_min NUMERIC(10,2) NOT NULL DEFAULT 0,
    min_batch      INT           NOT NULL DEFAULT 1,
    resource_group VARCHAR(64),
    UNIQUE (routing_id, seq),
    UNIQUE (routing_id, code)
);

CREATE TABLE md_operation_link (
    routing_id BIGINT      NOT NULL REFERENCES md_routing(id) ON DELETE CASCADE,
    pred_seq   INT         NOT NULL,
    succ_seq   INT         NOT NULL,
    link_type  VARCHAR(8)  NOT NULL DEFAULT 'FS' CHECK (link_type IN ('FS','SS','FF','SF')),
    lag_min    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (routing_id, pred_seq, succ_seq),
    CHECK (pred_seq <> succ_seq)
);

-- ---------------------------------------------------------------------------
-- 5. 库存（P0 只读，来自 ERP/WMS）
-- ---------------------------------------------------------------------------
CREATE TABLE inv_stock (
    id             BIGSERIAL PRIMARY KEY,
    plant_id       BIGINT        NOT NULL REFERENCES md_plant(id),
    item_id        BIGINT        NOT NULL REFERENCES md_item(id),
    warehouse      VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT',
    on_hand_qty    NUMERIC(14,3) NOT NULL DEFAULT 0,
    allocated_qty  NUMERIC(14,3) NOT NULL DEFAULT 0,
    available_qty  NUMERIC(14,3) NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (plant_id, item_id, warehouse)
);

CREATE TABLE inv_incoming (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT        NOT NULL REFERENCES md_plant(id),
    item_id     BIGINT        NOT NULL REFERENCES md_item(id),
    eta_date    DATE          NOT NULL,
    qty         NUMERIC(14,3) NOT NULL CHECK (qty > 0),
    source_no   VARCHAR(64),
    status      VARCHAR(16)   NOT NULL DEFAULT 'OPEN'
                CHECK (status IN ('OPEN','PARTIAL','DONE','CANCELLED'))
);
CREATE INDEX idx_incoming_item ON inv_incoming(plant_id, item_id, eta_date);

-- ---------------------------------------------------------------------------
-- 6. 订单
-- ---------------------------------------------------------------------------
CREATE TABLE ord_production_order (
    id                BIGSERIAL PRIMARY KEY,
    plant_id          BIGINT        NOT NULL REFERENCES md_plant(id),
    order_no          VARCHAR(64)   NOT NULL,
    item_id           BIGINT        NOT NULL REFERENCES md_item(id),
    routing_id        BIGINT        NOT NULL REFERENCES md_routing(id),
    qty               NUMERIC(14,3) NOT NULL CHECK (qty > 0),
    due_date          TIMESTAMPTZ   NOT NULL,
    priority          SMALLINT      NOT NULL DEFAULT 5 CHECK (priority BETWEEN 1 AND 9),
    status            VARCHAR(24)   NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','SCHEDULED','RELEASED','IN_PROGRESS','DONE','CANCELLED')),
    material_ready_ts TIMESTAMPTZ,
    src_system        VARCHAR(32),
    src_id            VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (plant_id, order_no)
);
CREATE INDEX idx_order_status_due ON ord_production_order(plant_id, status, due_date);
CREATE INDEX idx_order_priority   ON ord_production_order(plant_id, priority, due_date);
-- 幂等：同一来源系统的同一单据只允许一条
CREATE UNIQUE INDEX uq_order_src ON ord_production_order(src_system, src_id)
    WHERE src_id IS NOT NULL;

CREATE TABLE ord_order_operation (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES ord_production_order(id) ON DELETE CASCADE,
    op_seq         INT           NOT NULL,
    op_code        VARCHAR(32)   NOT NULL,
    qty            NUMERIC(14,3) NOT NULL,
    std_time_min   NUMERIC(10,2) NOT NULL,
    min_batch      INT           NOT NULL DEFAULT 1,
    duration_min   NUMERIC(10,2) NOT NULL CHECK (duration_min > 0),
    setup_min      NUMERIC(10,2) NOT NULL DEFAULT 0,
    resource_group VARCHAR(64),
    UNIQUE (order_id, op_seq)
);

CREATE TABLE ord_material_requirement (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT        NOT NULL REFERENCES ord_production_order(id) ON DELETE CASCADE,
    item_code     VARCHAR(64)   NOT NULL,
    at_op_seq     INT,
    required_qty  NUMERIC(14,3) NOT NULL,
    available_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
    shortage_qty  NUMERIC(14,3) NOT NULL DEFAULT 0,
    eta_ts        TIMESTAMPTZ
);
CREATE INDEX idx_matreq_order ON ord_material_requirement(order_id);

-- ---------------------------------------------------------------------------
-- 7. 排产批次与计划
-- ---------------------------------------------------------------------------
CREATE TABLE plan_run (
    id           BIGSERIAL PRIMARY KEY,
    plant_id     BIGINT      NOT NULL REFERENCES md_plant(id),
    run_no       VARCHAR(64) NOT NULL UNIQUE,
    params       JSONB       NOT NULL,
    input_digest VARCHAR(64) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'QUEUED'
                 CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    progress     SMALLINT    NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    message      TEXT,
    started_at   TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_run_status ON plan_run(plant_id, status, created_at DESC);
-- 单飞：同一工厂同时只允许一个未完成的排产任务
CREATE UNIQUE INDEX uq_run_singleflight ON plan_run(plant_id)
    WHERE status IN ('QUEUED','RUNNING');

-- 输入快照：保证"当时为什么这么排"可回放
CREATE TABLE plan_run_input (
    run_id      BIGINT      PRIMARY KEY REFERENCES plan_run(id) ON DELETE CASCADE,
    payload     JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE plan_version (
    id            BIGSERIAL PRIMARY KEY,
    plant_id      BIGINT      NOT NULL REFERENCES md_plant(id),
    run_id        BIGINT      NOT NULL REFERENCES plan_run(id),
    version_no    VARCHAR(32) NOT NULL,
    horizon_start DATE        NOT NULL,
    horizon_end   DATE        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT','IN_REVIEW','PUBLISHED','ARCHIVED','CANCELLED')),
    score         NUMERIC(8,2),
    kpi           JSONB,
    frozen        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (plant_id, version_no),
    CHECK (horizon_end > horizon_start)
);
-- 同一工厂同一计划起点同时只能有一个已发布版本
CREATE UNIQUE INDEX uq_version_published ON plan_version(plant_id, horizon_start)
    WHERE status = 'PUBLISHED';

CREATE TABLE plan_task (
    id          BIGSERIAL PRIMARY KEY,
    version_id  BIGINT      NOT NULL REFERENCES plan_version(id) ON DELETE CASCADE,
    order_op_id BIGINT      NOT NULL REFERENCES ord_order_operation(id),
    resource_id BIGINT      NOT NULL REFERENCES md_resource(id),
    start_ts    TIMESTAMPTZ NOT NULL,
    end_ts      TIMESTAMPTZ NOT NULL,
    seq_no      INT         NOT NULL,
    setup_min   NUMERIC(10,2) NOT NULL DEFAULT 0,
    -- 不可变快照字段：主数据或订单变更后，历史计划语义不漂移
    item_code   VARCHAR(64) NOT NULL,
    family      VARCHAR(64),
    qty         NUMERIC(14,3) NOT NULL,
    duration_min NUMERIC(10,2) NOT NULL,
    locked      BOOLEAN     NOT NULL DEFAULT FALSE,   -- P0 恒为 false，P1 启用
    source      VARCHAR(16) NOT NULL DEFAULT 'ENGINE' CHECK (source IN ('ENGINE','MANUAL')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_ts > start_ts)
);
CREATE INDEX idx_task_version_res_time ON plan_task(version_id, resource_id, start_ts);
CREATE INDEX idx_task_version_order    ON plan_task(version_id, order_op_id);

-- 加工分段：跨班次/跨天时一个任务对应多段占用
CREATE TABLE plan_task_segment (
    id       BIGSERIAL PRIMARY KEY,
    task_id  BIGINT      NOT NULL REFERENCES plan_task(id) ON DELETE CASCADE,
    seq_no   SMALLINT    NOT NULL,
    start_ts TIMESTAMPTZ NOT NULL,
    end_ts   TIMESTAMPTZ NOT NULL,
    UNIQUE (task_id, seq_no),
    CHECK (end_ts > start_ts)
);
CREATE INDEX idx_segment_task ON plan_task_segment(task_id, start_ts);

-- P1 预置：多资源同时占用（机台 + 模具 + 人员）
CREATE TABLE plan_task_resource (
    task_id     BIGINT      NOT NULL REFERENCES plan_task(id) ON DELETE CASCADE,
    resource_id BIGINT      NOT NULL REFERENCES md_resource(id),
    res_role    VARCHAR(16) NOT NULL CHECK (res_role IN ('MACHINE','TOOL','CREW')),
    qty         NUMERIC(8,2) NOT NULL DEFAULT 1,
    PRIMARY KEY (task_id, resource_id, res_role)
);

CREATE TABLE plan_task_trace (
    task_id     BIGINT PRIMARY KEY REFERENCES plan_task(id) ON DELETE CASCADE,
    candidates  JSONB,
    constraints JSONB,
    detail      JSONB
);

-- ---------------------------------------------------------------------------
-- 8. 未排原因（B4）
-- ---------------------------------------------------------------------------
CREATE TABLE dict_reason_code (
    code         VARCHAR(16) PRIMARY KEY,
    level        VARCHAR(16) NOT NULL CHECK (level IN ('ORDER','OPERATION')),
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(255),
    suggestion   VARCHAR(255),
    sort_no      SMALLINT    NOT NULL DEFAULT 100,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE plan_unscheduled (
    id                   BIGSERIAL PRIMARY KEY,
    run_id               BIGINT      NOT NULL REFERENCES plan_run(id) ON DELETE CASCADE,
    version_id           BIGINT      REFERENCES plan_version(id) ON DELETE CASCADE,
    order_id             BIGINT      NOT NULL REFERENCES ord_production_order(id),
    order_op_id          BIGINT      REFERENCES ord_order_operation(id),
    op_seq               INT,
    level                VARCHAR(16) NOT NULL CHECK (level IN ('ORDER','OPERATION')),
    reason_code          VARCHAR(16) NOT NULL REFERENCES dict_reason_code(code),
    blocking_resource_id BIGINT      REFERENCES md_resource(id),
    earliest_feasible_ts TIMESTAMPTZ,
    gap_qty              NUMERIC(14,3),
    detail               JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_unsched_run    ON plan_unscheduled(run_id, reason_code);
CREATE INDEX idx_unsched_order  ON plan_unscheduled(order_id);

INSERT INTO dict_reason_code (code, level, name, description, suggestion, sort_no) VALUES
 ('MAT_001','ORDER','缺料','物料可用量不足或到料晚于需求时点','确认到料计划或调整交期',10),
 ('RES_002','OPERATION','无可用资源','工序所需能力没有任何资源匹配','维护资源能力或工艺替代资源',20),
 ('HOR_003','ORDER','超出计划展望期','交期或齐套时点落在计划窗口之外','纳入后续计划周期',30),
 ('BOM_004','ORDER','主数据缺失','物料缺工艺路线、工序缺标准工时等','指派主数据维护',40),
 ('CAP_005','OPERATION','产能不足','计划窗口内无法容纳该工序工时与换型','加班、增开资源或顺延',50),
 ('CAL_006','OPERATION','日历不可用','资源在窗口内无可用工作时段','检查日历与停机设置',60),
 ('FRZ_007','OPERATION','冻结区冲突','任务起点早于冻结截止时间（P1）','调整冻结策略或顺延',70),
 ('PRI_008','OPERATION','优先级挤占','被更高优先级订单占满窗口','提升优先级或拆分订单',80)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 9. 发布、审批与下达
-- ---------------------------------------------------------------------------
CREATE TABLE plan_approval (
    id          BIGSERIAL PRIMARY KEY,
    version_id  BIGINT      NOT NULL REFERENCES plan_version(id) ON DELETE CASCADE,
    step_no     SMALLINT    NOT NULL,
    approver_id BIGINT,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','APPROVED','REJECTED','SKIPPED')),
    comment     VARCHAR(500),
    acted_at    TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (version_id, step_no)
);
CREATE INDEX idx_approval_pending ON plan_approval(approver_id, status);

CREATE TABLE plan_publish (
    id             BIGSERIAL PRIMARY KEY,
    version_id     BIGINT      NOT NULL UNIQUE REFERENCES plan_version(id),
    published_by   BIGINT,
    published_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_from TIMESTAMPTZ,
    note           VARCHAR(500)
);

CREATE TABLE dispatch_record (
    id               BIGSERIAL PRIMARY KEY,
    version_id       BIGINT      NOT NULL REFERENCES plan_version(id),
    target_system    VARCHAR(32) NOT NULL DEFAULT 'MES',
    idempotency_key  VARCHAR(64) NOT NULL UNIQUE,
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','SENT','ACKED','FAILED','PARTIAL')),
    retry_count      SMALLINT    NOT NULL DEFAULT 0,
    request_payload  JSONB,
    response_payload JSONB,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dispatch_version ON dispatch_record(version_id, status);

CREATE TABLE dispatch_item (
    id           BIGSERIAL PRIMARY KEY,
    dispatch_id  BIGINT      NOT NULL REFERENCES dispatch_record(id) ON DELETE CASCADE,
    plan_task_id BIGINT      NOT NULL REFERENCES plan_task(id),
    order_op_id  BIGINT      NOT NULL REFERENCES ord_order_operation(id),
    work_order_no VARCHAR(64),
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','SENT','ACKED','FAILED')),
    UNIQUE (dispatch_id, plan_task_id)
);

-- ---------------------------------------------------------------------------
-- 10. 用户、角色与权限
-- ---------------------------------------------------------------------------
CREATE TABLE sys_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    display_name  VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    plant_id      BIGINT       REFERENCES md_plant(id),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_permission (
    id     BIGSERIAL PRIMARY KEY,
    code   VARCHAR(64) NOT NULL UNIQUE,
    name   VARCHAR(64) NOT NULL,
    module VARCHAR(32)
);

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 数据权限：用户可访问的工厂/车间范围
CREATE TABLE sys_user_data_scope (
    user_id    BIGINT      NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    scope_type VARCHAR(16) NOT NULL CHECK (scope_type IN ('PLANT','WORKSHOP')),
    scope_id   BIGINT      NOT NULL,
    PRIMARY KEY (user_id, scope_type, scope_id)
);

-- ---------------------------------------------------------------------------
-- 11. 审计与幂等
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT,
    action      VARCHAR(64) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id   VARCHAR(64),
    before_data JSONB,
    after_data  JSONB,
    ip          INET,
    trace_id    VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_object ON audit_log(object_type, object_id, created_at DESC);
CREATE INDEX idx_audit_actor  ON audit_log(actor_id, created_at DESC);

CREATE TABLE idempotency_record (
    idem_key     VARCHAR(128) PRIMARY KEY,
    endpoint     VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64)  NOT NULL,
    response     JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ
);

-- ---------------------------------------------------------------------------
-- 12. updated_at 触发器
-- ---------------------------------------------------------------------------
CREATE TRIGGER trg_resource_updated  BEFORE UPDATE ON md_resource
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_order_updated     BEFORE UPDATE ON ord_production_order
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_version_updated   BEFORE UPDATE ON plan_version
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_dispatch_updated  BEFORE UPDATE ON dispatch_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_user_updated      BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 13. 延迟外键（建表顺序无法前向引用）
-- ---------------------------------------------------------------------------
ALTER TABLE md_resource
    ADD CONSTRAINT fk_resource_workshop FOREIGN KEY (workshop_id) REFERENCES md_resource(id);

ALTER TABLE md_calendar_exception
    ADD CONSTRAINT fk_cal_ex_resource FOREIGN KEY (resource_id) REFERENCES md_resource(id);

ALTER TABLE md_alt_resource
    ADD CONSTRAINT fk_alt_res_op FOREIGN KEY (operation_id) REFERENCES md_operation(id) ON DELETE CASCADE;

ALTER TABLE plan_run
    ADD CONSTRAINT fk_run_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id);

ALTER TABLE plan_approval
    ADD CONSTRAINT fk_approval_user FOREIGN KEY (approver_id) REFERENCES sys_user(id);

ALTER TABLE plan_publish
    ADD CONSTRAINT fk_publish_user FOREIGN KEY (published_by) REFERENCES sys_user(id);

ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id);

-- ---------------------------------------------------------------------------
-- 14. 初始数据
-- ---------------------------------------------------------------------------
INSERT INTO md_plant (code, name) VALUES ('P01', '默认工厂')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role (code, name, description) VALUES
 ('ADMIN',      '系统管理员', '全部权限'),
 ('PLANNER',    '主计划员',   '排产、调整、发布'),
 ('SUPERVISOR', '生产经理',   '审批、查看'),
 ('VIEWER',     '只读用户',   '查看计划与报表')
ON CONFLICT (code) DO NOTHING;
