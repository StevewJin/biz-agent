-- ============================================================
-- BizAgent 数据库建表脚本（PostgreSQL）
-- 用法: psql -h <host> -U <user> -d <db> -f backend/schema.sql
-- 可重复执行：均为 CREATE ... IF NOT EXISTS / ALTER TABLE ... ADD COLUMN IF NOT EXISTS
-- ============================================================
CREATE SCHEMA IF NOT EXISTS bi;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL,
    email           VARCHAR(100)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role_type       VARCHAR(20)     NOT NULL DEFAULT 'user',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_users IS '用户表';
COMMENT ON COLUMN bi.ba_users.id            IS '主键标识';
COMMENT ON COLUMN bi.ba_users.username      IS '用户名（唯一）';
COMMENT ON COLUMN bi.ba_users.email         IS '邮箱（唯一）';
COMMENT ON COLUMN bi.ba_users.password_hash IS '密码哈希';
COMMENT ON COLUMN bi.ba_users.role_type          IS '用户角色：普通用户 / 管理员';
COMMENT ON COLUMN bi.ba_users.created_at    IS '创建时间';
COMMENT ON COLUMN bi.ba_users.updated_at    IS '更新时间';

-- ============================================================
-- 2. 团队表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_teams (
    id          BIGSERIAL       PRIMARY KEY,
    team_name       VARCHAR(100)    NOT NULL,
    description VARCHAR(2000),
    owner_id    BIGINT          NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_teams IS '团队表';
COMMENT ON COLUMN bi.ba_teams.id          IS '主键标识';
COMMENT ON COLUMN bi.ba_teams.team_name        IS '团队名称';
COMMENT ON COLUMN bi.ba_teams.description IS '描述';
COMMENT ON COLUMN bi.ba_teams.owner_id    IS '所属用户标识';
COMMENT ON COLUMN bi.ba_teams.created_at  IS '创建时间';
COMMENT ON COLUMN bi.ba_teams.updated_at  IS '更新时间';

-- ============================================================
-- 3. 团队成员表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_team_members (
    id         BIGSERIAL    PRIMARY KEY,
    team_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    role_type       VARCHAR(20)  NOT NULL DEFAULT 'member',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_team_members IS '团队成员关联表';
COMMENT ON COLUMN bi.ba_team_members.id         IS '主键标识';
COMMENT ON COLUMN bi.ba_team_members.team_id    IS '所属团队标识';
COMMENT ON COLUMN bi.ba_team_members.user_id    IS '所属用户标识';
COMMENT ON COLUMN bi.ba_team_members.role_type       IS '团队成员角色：拥有者 / 管理员 / 成员';
COMMENT ON COLUMN bi.ba_team_members.created_at IS '创建时间';

-- ============================================================
-- 4. LLM 供应商表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_llm_providers (
    id                BIGSERIAL       PRIMARY KEY,
    provider_name   VARCHAR(100)    NOT NULL,
    provider_type     VARCHAR(50)     NOT NULL,
    api_key_encrypted VARCHAR(500)            NOT NULL,
    base_url          VARCHAR(500)    NOT NULL,
    models            VARCHAR(2000)           NOT NULL DEFAULT '[]',
    is_default        BOOLEAN         NOT NULL DEFAULT FALSE,
    enabled           BOOLEAN         NOT NULL DEFAULT TRUE,
    owner_id          BIGINT,
    team_id           BIGINT,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_llm_providers IS '大模型供应商配置表';
COMMENT ON COLUMN bi.ba_llm_providers.id                IS '主键标识';
COMMENT ON COLUMN bi.ba_llm_providers.provider_name              IS '供应商显示名称';
COMMENT ON COLUMN bi.ba_llm_providers.provider_type     IS '供应商类型标识（如开放人工智能 / 深度求索 / 克劳德）';
COMMENT ON COLUMN bi.ba_llm_providers.api_key_encrypted IS '加密后的接口密钥';
COMMENT ON COLUMN bi.ba_llm_providers.base_url          IS '接口地址';
COMMENT ON COLUMN bi.ba_llm_providers.models            IS '可用模型列表';
COMMENT ON COLUMN bi.ba_llm_providers.is_default        IS '是否为默认供应商';
COMMENT ON COLUMN bi.ba_llm_providers.enabled           IS '是否启用';
COMMENT ON COLUMN bi.ba_llm_providers.owner_id          IS '所属用户标识';
COMMENT ON COLUMN bi.ba_llm_providers.team_id           IS '所属团队标识';
COMMENT ON COLUMN bi.ba_llm_providers.created_at        IS '创建时间';
COMMENT ON COLUMN bi.ba_llm_providers.updated_at        IS '更新时间';

-- ============================================================
-- 5. 智能体表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_agents (
    id            BIGSERIAL       PRIMARY KEY,
    agent_name      VARCHAR(100)    NOT NULL,
    logo          VARCHAR(500),
    description   VARCHAR(2000),
    system_prompt VARCHAR(5000),
    owner_id      BIGINT          NOT NULL,
    team_id       BIGINT,
    visibility     VARCHAR(20)     NOT NULL DEFAULT 'personal',
    provider_id   BIGINT,
    aes_key       VARCHAR(64),
    share_token   VARCHAR(36),
    enabled       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_agents IS '智能体表';
COMMENT ON COLUMN bi.ba_agents.id            IS '主键标识';
COMMENT ON COLUMN bi.ba_agents.agent_name          IS '智能体名称';
COMMENT ON COLUMN bi.ba_agents.logo          IS '徽标地址';
COMMENT ON COLUMN bi.ba_agents.description   IS '描述';
COMMENT ON COLUMN bi.ba_agents.system_prompt IS '系统提示词';
COMMENT ON COLUMN bi.ba_agents.owner_id      IS '所属用户标识';
COMMENT ON COLUMN bi.ba_agents.team_id       IS '所属团队标识';
COMMENT ON COLUMN bi.ba_agents.visibility         IS '作用域：个人 / 团队';
COMMENT ON COLUMN bi.ba_agents.provider_id   IS '关联的大模型供应商标识';
COMMENT ON COLUMN bi.ba_agents.aes_key       IS 'AES 加密密钥';
COMMENT ON COLUMN bi.ba_agents.share_token   IS '分享令牌（唯一）';
COMMENT ON COLUMN bi.ba_agents.enabled       IS '是否启用';
COMMENT ON COLUMN bi.ba_agents.created_at    IS '创建时间';
COMMENT ON COLUMN bi.ba_agents.updated_at    IS '更新时间';

-- ============================================================
-- 6. 技能包表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_skills (
    id             BIGSERIAL       PRIMARY KEY,
    skill_code     VARCHAR(100)    NOT NULL UNIQUE,
    display_name   VARCHAR(200)    NOT NULL,
    description    VARCHAR(2000)   NOT NULL,
    category       VARCHAR(50)     NOT NULL DEFAULT 'general',
    prompt_md      TEXT            NOT NULL,
    resources_json VARCHAR(5000),
    config_schema  VARCHAR(5000),
    keywords       VARCHAR(500),
    trigger_mode   VARCHAR(20)     NOT NULL DEFAULT 'auto',
    schema_mode    VARCHAR(20)     NOT NULL DEFAULT 'inline',
    skill_version  VARCHAR(50),
    pack_source    VARCHAR(20)     NOT NULL DEFAULT 'manual',
    manifest_json  VARCHAR(5000),
    icon           VARCHAR(50),
    icon_color     VARCHAR(50),
    owner_id       BIGINT,
    team_id        BIGINT,
    visibility     VARCHAR(20)     NOT NULL DEFAULT 'personal',
    publish_status VARCHAR(20)     NOT NULL DEFAULT 'draft',
    reject_reason  VARCHAR(500),
    published_at   TIMESTAMP,
    enabled        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_skills IS '技能包表';
COMMENT ON COLUMN bi.ba_skills.id             IS '主键标识';
COMMENT ON COLUMN bi.ba_skills.skill_code     IS '技能唯一标识名';
COMMENT ON COLUMN bi.ba_skills.display_name   IS '技能显示名称';
COMMENT ON COLUMN bi.ba_skills.description    IS '描述';
COMMENT ON COLUMN bi.ba_skills.category       IS '技能分类';
COMMENT ON COLUMN bi.ba_skills.prompt_md      IS '技能提示词（Markdown，支持 {{datasource:code}} 等占位符）';
COMMENT ON COLUMN bi.ba_skills.resources_json IS '引用的资源（数据源/接口/扩展包/工具）';
COMMENT ON COLUMN bi.ba_skills.config_schema  IS '配置项结构定义';
COMMENT ON COLUMN bi.ba_skills.keywords       IS '触发关键词（逗号分隔）';
COMMENT ON COLUMN bi.ba_skills.trigger_mode   IS '触发方式：auto / keyword / manual';
COMMENT ON COLUMN bi.ba_skills.schema_mode    IS '表结构注入方式：inline（内联字段）/ lazy（仅表名，按需查询）';
COMMENT ON COLUMN bi.ba_skills.skill_version  IS '技能版本';
COMMENT ON COLUMN bi.ba_skills.pack_source    IS '来源：manual / upload / builtin';
COMMENT ON COLUMN bi.ba_skills.manifest_json  IS '技能包清单';
COMMENT ON COLUMN bi.ba_skills.icon           IS '图标';
COMMENT ON COLUMN bi.ba_skills.icon_color     IS '图标颜色';
COMMENT ON COLUMN bi.ba_skills.owner_id       IS '创建人标识（为空表示系统内置，仅管理员可改）';
COMMENT ON COLUMN bi.ba_skills.team_id        IS '归属团队（为空表示个人）';
COMMENT ON COLUMN bi.ba_skills.visibility     IS '可见范围：personal / team / public';
COMMENT ON COLUMN bi.ba_skills.publish_status IS '上架状态：draft / pending / approved / rejected';
COMMENT ON COLUMN bi.ba_skills.reject_reason  IS '审核驳回原因';
COMMENT ON COLUMN bi.ba_skills.published_at   IS '审核通过时间';
COMMENT ON COLUMN bi.ba_skills.enabled        IS '是否启用';
COMMENT ON COLUMN bi.ba_skills.created_at     IS '创建时间';
COMMENT ON COLUMN bi.ba_skills.updated_at     IS '更新时间';

-- ============================================================
-- 7. 智能体-技能关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_agent_skills (
    id               BIGSERIAL    PRIMARY KEY,
    agent_id         BIGINT       NOT NULL,
    skill_id         BIGINT       NOT NULL,
    config_overrides VARCHAR(5000),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_agent_skills IS '智能体与技能关联表';
COMMENT ON COLUMN bi.ba_agent_skills.id               IS '主键标识';
COMMENT ON COLUMN bi.ba_agent_skills.agent_id         IS '关联的智能体标识';
COMMENT ON COLUMN bi.ba_agent_skills.skill_id         IS '技能标识';
COMMENT ON COLUMN bi.ba_agent_skills.config_overrides IS '配置覆盖项';
COMMENT ON COLUMN bi.ba_agent_skills.created_at       IS '创建时间';

-- ============================================================
-- 8. 技能配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_skill_configs (
    id         BIGSERIAL    PRIMARY KEY,
    skill_id   BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    config     VARCHAR(5000)        NOT NULL DEFAULT '{}',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_skill_configs IS '用户技能配置表';
COMMENT ON COLUMN bi.ba_skill_configs.id         IS '主键标识';
COMMENT ON COLUMN bi.ba_skill_configs.skill_id   IS '技能标识';
COMMENT ON COLUMN bi.ba_skill_configs.user_id    IS '所属用户标识';
COMMENT ON COLUMN bi.ba_skill_configs.config     IS '个性化配置';
COMMENT ON COLUMN bi.ba_skill_configs.created_at IS '创建时间';
COMMENT ON COLUMN bi.ba_skill_configs.updated_at IS '更新时间';

-- ============================================================
-- 9. 对话表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_conversations (
    id            BIGSERIAL       PRIMARY KEY,
    user_id       BIGINT          NOT NULL,
    title         VARCHAR(200)    NOT NULL DEFAULT '新对话',
    status        VARCHAR(20)     NOT NULL DEFAULT 'active',
    provider_id   BIGINT,
    model_name      VARCHAR(100)    NOT NULL DEFAULT 'deepseek-flash',
    agent_id      BIGINT,
    team_id       BIGINT,
    system_prompt VARCHAR(5000),
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_conversations IS '对话表';
COMMENT ON COLUMN bi.ba_conversations.id            IS '主键标识';
COMMENT ON COLUMN bi.ba_conversations.user_id       IS '所属用户标识';
COMMENT ON COLUMN bi.ba_conversations.title         IS '对话标题';
COMMENT ON COLUMN bi.ba_conversations.status        IS '状态：进行中 / 已归档 / 已删除';
COMMENT ON COLUMN bi.ba_conversations.provider_id   IS '关联的大模型供应商标识';
COMMENT ON COLUMN bi.ba_conversations.model_name         IS '使用的模型名称';
COMMENT ON COLUMN bi.ba_conversations.agent_id      IS '关联的智能体标识';
COMMENT ON COLUMN bi.ba_conversations.team_id       IS '所属团队标识';
COMMENT ON COLUMN bi.ba_conversations.system_prompt IS '系统提示词';
COMMENT ON COLUMN bi.ba_conversations.created_at    IS '创建时间';
COMMENT ON COLUMN bi.ba_conversations.updated_at    IS '更新时间';

-- ============================================================
-- 10. 对话-技能关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_conversation_skills (
    id               BIGSERIAL    PRIMARY KEY,
    conversation_id  BIGINT       NOT NULL,
    skill_id         BIGINT       NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    config_overrides VARCHAR(5000),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_conversation_skills IS '对话与技能关联表';
COMMENT ON COLUMN bi.ba_conversation_skills.id               IS '主键标识';
COMMENT ON COLUMN bi.ba_conversation_skills.conversation_id  IS '所属对话标识';
COMMENT ON COLUMN bi.ba_conversation_skills.skill_id         IS '技能标识';
COMMENT ON COLUMN bi.ba_conversation_skills.enabled          IS '是否启用';
COMMENT ON COLUMN bi.ba_conversation_skills.config_overrides IS '配置覆盖项';
COMMENT ON COLUMN bi.ba_conversation_skills.created_at       IS '创建时间';

-- ============================================================
-- 11. 消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_messages (
    id                BIGSERIAL    PRIMARY KEY,
    conversation_id   BIGINT       NOT NULL,
    message_role    VARCHAR(20)  NOT NULL,
    message_content VARCHAR(5000),
    tool_call_id      VARCHAR(100),
    tool_name         VARCHAR(100),
    tool_input        VARCHAR(5000),
    reasoning_content VARCHAR(5000),
    tool_result       VARCHAR(5000),
    sequence_num      INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_messages IS '消息表';
COMMENT ON COLUMN bi.ba_messages.id                IS '主键标识';
COMMENT ON COLUMN bi.ba_messages.conversation_id   IS '所属对话标识';
COMMENT ON COLUMN bi.ba_messages.message_role              IS '消息角色：用户 / 助手 / 工具';
COMMENT ON COLUMN bi.ba_messages.message_content           IS '消息正文';
COMMENT ON COLUMN bi.ba_messages.tool_call_id      IS '工具调用标识';
COMMENT ON COLUMN bi.ba_messages.tool_name         IS '工具名称';
COMMENT ON COLUMN bi.ba_messages.tool_input        IS '工具调用参数';
COMMENT ON COLUMN bi.ba_messages.reasoning_content IS '推理或思考过程内容';
COMMENT ON COLUMN bi.ba_messages.tool_result       IS '工具调用结果';
COMMENT ON COLUMN bi.ba_messages.sequence_num      IS '序号';
COMMENT ON COLUMN bi.ba_messages.created_at        IS '创建时间';

-- ============================================================
-- 12. 思维链条目表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_thinking_chain_entries (
    id           BIGSERIAL    PRIMARY KEY,
    message_id   BIGINT       NOT NULL,
    sequence_num INTEGER      NOT NULL,
    entry_type      VARCHAR(20)  NOT NULL,
    entry_content   VARCHAR(5000),
    tool_call_id VARCHAR(100),
    tool_name    VARCHAR(100),
    tool_input   VARCHAR(5000),
    tool_display_name VARCHAR(200),
    extension_code    VARCHAR(100),
    extension_name    VARCHAR(200),
    tool_kind         VARCHAR(20),
    skill_name        VARCHAR(200),
    duration_ms  BIGINT,
    render_json  VARCHAR(10000),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_thinking_chain_entries IS '思维链条目表';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.id           IS '主键标识';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.message_id   IS '所属消息标识';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.sequence_num IS '序号';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.entry_type         IS '类型：文本 / 思考 / 工具调用 / 工具结果';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.entry_content      IS '内容';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.tool_call_id IS '工具调用标识';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.tool_name    IS '工具名称';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.tool_input   IS '工具调用参数';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.tool_display_name IS '工具展示名（来自扩展包 manifest）';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.extension_code    IS '所属扩展包编码';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.extension_name    IS '所属扩展包名称';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.tool_kind         IS '工具类型：sql / http / render / export / jar';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.skill_name        IS '触发该工具的技能包名称';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.duration_ms  IS '执行耗时（毫秒）';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.render_json  IS '前端渲染负载（_render 协议）';
COMMENT ON COLUMN bi.ba_thinking_chain_entries.created_at   IS '创建时间';

-- ============================================================
-- 13. 分享会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_share_sessions (
    id                 BIGSERIAL    PRIMARY KEY,
    conversation_id    BIGINT       NOT NULL,
    agent_id           BIGINT,
    external_username  VARCHAR(200),
    ip_address         VARCHAR(45),
    message_count      INTEGER      NOT NULL DEFAULT 0,
    conversation_count INTEGER      NOT NULL DEFAULT 1,
    expired            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_share_sessions IS '分享会话表（智能体公开访问）';
COMMENT ON COLUMN bi.ba_share_sessions.id                 IS '主键标识';
COMMENT ON COLUMN bi.ba_share_sessions.conversation_id    IS '所属对话标识';
COMMENT ON COLUMN bi.ba_share_sessions.agent_id           IS '关联的智能体标识';
COMMENT ON COLUMN bi.ba_share_sessions.external_username  IS '外部访客用户名';
COMMENT ON COLUMN bi.ba_share_sessions.ip_address         IS '访客 IP 地址';
COMMENT ON COLUMN bi.ba_share_sessions.message_count      IS '消息数量';
COMMENT ON COLUMN bi.ba_share_sessions.conversation_count IS '对话数量';
COMMENT ON COLUMN bi.ba_share_sessions.expired            IS '是否已过期';
COMMENT ON COLUMN bi.ba_share_sessions.created_at         IS '创建时间';
COMMENT ON COLUMN bi.ba_share_sessions.updated_at         IS '更新时间';
-- ============================================================
-- 14. 业务系统数据库（数据源）表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_datasources (
    id                 BIGSERIAL       PRIMARY KEY,
    ds_code            VARCHAR(100)    NOT NULL UNIQUE,
    ds_name            VARCHAR(200)    NOT NULL,
    db_type            VARCHAR(50)     NOT NULL DEFAULT 'postgresql',
    jdbc_url           VARCHAR(1000)   NOT NULL,
    driver_class       VARCHAR(200),
    driver_jar_path    VARCHAR(500),
    username           VARCHAR(200),
    password_encrypted VARCHAR(1000),
    schema_name        VARCHAR(100),
    read_only          BOOLEAN         NOT NULL DEFAULT TRUE,
    query_max_rows     INTEGER         NOT NULL DEFAULT 2000,
    query_timeout_sec  INTEGER         NOT NULL DEFAULT 30,
    owner_id           BIGINT          NOT NULL,
    team_id            BIGINT,
    visibility         VARCHAR(20)     NOT NULL DEFAULT 'personal',
    description        VARCHAR(2000),
    last_test_at       TIMESTAMP,
    last_test_ok       BOOLEAN,
    enabled            BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_datasources IS '业务系统数据库（数据源）表';
COMMENT ON COLUMN bi.ba_datasources.ds_code            IS '数据源唯一编码';
COMMENT ON COLUMN bi.ba_datasources.ds_name            IS '数据源名称';
COMMENT ON COLUMN bi.ba_datasources.db_type            IS '数据库类型：postgresql / mysql / oracle / panweidb';
COMMENT ON COLUMN bi.ba_datasources.jdbc_url           IS 'JDBC 连接地址';
COMMENT ON COLUMN bi.ba_datasources.driver_class       IS '驱动类名';
COMMENT ON COLUMN bi.ba_datasources.driver_jar_path    IS '外部驱动 jar 路径（可选）';
COMMENT ON COLUMN bi.ba_datasources.username           IS '连接用户名';
COMMENT ON COLUMN bi.ba_datasources.password_encrypted IS '加密后的连接密码';
COMMENT ON COLUMN bi.ba_datasources.schema_name        IS '默认模式名';
COMMENT ON COLUMN bi.ba_datasources.read_only          IS '是否只读访问';
COMMENT ON COLUMN bi.ba_datasources.query_max_rows     IS '单次查询最大返回行数';
COMMENT ON COLUMN bi.ba_datasources.query_timeout_sec  IS '查询超时时间（秒）';
COMMENT ON COLUMN bi.ba_datasources.owner_id           IS '所属用户标识';
COMMENT ON COLUMN bi.ba_datasources.team_id            IS '所属团队标识';
COMMENT ON COLUMN bi.ba_datasources.visibility         IS '作用域：个人 / 团队';
COMMENT ON COLUMN bi.ba_datasources.description        IS '描述';
COMMENT ON COLUMN bi.ba_datasources.last_test_at       IS '最近连接测试时间';
COMMENT ON COLUMN bi.ba_datasources.last_test_ok       IS '最近连接测试结果';
COMMENT ON COLUMN bi.ba_datasources.enabled            IS '是否启用';
COMMENT ON COLUMN bi.ba_datasources.created_at         IS '创建时间';
COMMENT ON COLUMN bi.ba_datasources.updated_at         IS '更新时间';

-- ============================================================
-- 15. 数据源表结构缓存
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_datasource_tables (
    id            BIGSERIAL     PRIMARY KEY,
    datasource_id BIGINT        NOT NULL,
    table_name    VARCHAR(200)  NOT NULL,
    table_comment VARCHAR(1000),
    row_estimate  BIGINT,
    synced_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_datasource_tables IS '数据源表清单缓存';
COMMENT ON COLUMN bi.ba_datasource_tables.datasource_id IS '所属数据源标识';
COMMENT ON COLUMN bi.ba_datasource_tables.table_name    IS '表名';
COMMENT ON COLUMN bi.ba_datasource_tables.table_comment IS '表注释';
COMMENT ON COLUMN bi.ba_datasource_tables.row_estimate  IS '行数估算';
COMMENT ON COLUMN bi.ba_datasource_tables.synced_at     IS '同步时间';

CREATE TABLE IF NOT EXISTS bi.ba_datasource_columns (
    id             BIGSERIAL     PRIMARY KEY,
    datasource_id  BIGINT        NOT NULL,
    table_name     VARCHAR(200)  NOT NULL,
    column_name    VARCHAR(200)  NOT NULL,
    data_type      VARCHAR(100),
    nullable       BOOLEAN,
    column_comment VARCHAR(1000),
    ordinal        INTEGER,
    synced_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_datasource_columns IS '数据源字段清单缓存';
COMMENT ON COLUMN bi.ba_datasource_columns.datasource_id  IS '所属数据源标识';
COMMENT ON COLUMN bi.ba_datasource_columns.table_name     IS '表名';
COMMENT ON COLUMN bi.ba_datasource_columns.column_name    IS '字段名';
COMMENT ON COLUMN bi.ba_datasource_columns.data_type      IS '字段类型';
COMMENT ON COLUMN bi.ba_datasource_columns.nullable       IS '是否可空';
COMMENT ON COLUMN bi.ba_datasource_columns.column_comment IS '字段注释';
COMMENT ON COLUMN bi.ba_datasource_columns.ordinal        IS '顺序';
COMMENT ON COLUMN bi.ba_datasource_columns.synced_at      IS '同步时间';

-- ============================================================
-- 16. 业务系统接口表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_api_endpoints (
    id                    BIGSERIAL       PRIMARY KEY,
    api_code              VARCHAR(100)    NOT NULL UNIQUE,
    api_name              VARCHAR(200)    NOT NULL,
    base_url              VARCHAR(500)    NOT NULL,
    path                  VARCHAR(500)    NOT NULL DEFAULT '',
    http_method           VARCHAR(10)     NOT NULL DEFAULT 'GET',
    auth_type             VARCHAR(20)     NOT NULL DEFAULT 'none',
    auth_config_encrypted VARCHAR(2000),
    default_headers       VARCHAR(2000),
    param_schema          VARCHAR(5000),
    response_schema       VARCHAR(5000),
    request_example       VARCHAR(2000),
    timeout_ms            INTEGER         NOT NULL DEFAULT 30000,
    owner_id              BIGINT          NOT NULL,
    team_id               BIGINT,
    visibility            VARCHAR(20)     NOT NULL DEFAULT 'personal',
    description           VARCHAR(2000),
    enabled               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_api_endpoints IS '业务系统接口表';
COMMENT ON COLUMN bi.ba_api_endpoints.api_code              IS '接口唯一编码';
COMMENT ON COLUMN bi.ba_api_endpoints.api_name              IS '接口名称';
COMMENT ON COLUMN bi.ba_api_endpoints.base_url              IS '服务基础地址（域名白名单）';
COMMENT ON COLUMN bi.ba_api_endpoints.path                  IS '接口路径（可含 {param} 占位符）';
COMMENT ON COLUMN bi.ba_api_endpoints.http_method           IS 'HTTP 方法';
COMMENT ON COLUMN bi.ba_api_endpoints.auth_type             IS '鉴权类型：none / bearer / basic / api_key';
COMMENT ON COLUMN bi.ba_api_endpoints.auth_config_encrypted IS '加密后的鉴权配置';
COMMENT ON COLUMN bi.ba_api_endpoints.default_headers       IS '默认请求头（JSON）';
COMMENT ON COLUMN bi.ba_api_endpoints.param_schema          IS '参数 JSON Schema';
COMMENT ON COLUMN bi.ba_api_endpoints.response_schema       IS '响应结构说明';
COMMENT ON COLUMN bi.ba_api_endpoints.request_example       IS '请求示例';
COMMENT ON COLUMN bi.ba_api_endpoints.timeout_ms            IS '超时时间（毫秒）';
COMMENT ON COLUMN bi.ba_api_endpoints.owner_id              IS '所属用户标识';
COMMENT ON COLUMN bi.ba_api_endpoints.team_id               IS '所属团队标识';
COMMENT ON COLUMN bi.ba_api_endpoints.visibility            IS '作用域：个人 / 团队';
COMMENT ON COLUMN bi.ba_api_endpoints.description           IS '描述';
COMMENT ON COLUMN bi.ba_api_endpoints.enabled               IS '是否启用';
COMMENT ON COLUMN bi.ba_api_endpoints.created_at            IS '创建时间';
COMMENT ON COLUMN bi.ba_api_endpoints.updated_at            IS '更新时间';

-- ============================================================
-- 17. 扩展包表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_extensions (
    id            BIGSERIAL       PRIMARY KEY,
    ext_code      VARCHAR(100)    NOT NULL,
    ext_name      VARCHAR(200)    NOT NULL,
    version       VARCHAR(50)     NOT NULL,
    pack_type     VARCHAR(20)     NOT NULL DEFAULT 'declarative',
    manifest_json VARCHAR(20000),
    jar_path      VARCHAR(1000),
    assets_path   VARCHAR(1000),
    entry_file    VARCHAR(500),
    sha256        VARCHAR(128),
    status        VARCHAR(20)     NOT NULL DEFAULT 'installed',
    system_pack   BOOLEAN         NOT NULL DEFAULT FALSE,
    error_msg     VARCHAR(2000),
    owner_id      BIGINT,
    team_id       BIGINT,
    visibility    VARCHAR(20)     NOT NULL DEFAULT 'personal',
    publish_status VARCHAR(20)    NOT NULL DEFAULT 'draft',
    reject_reason VARCHAR(500),
    published_at  TIMESTAMP,
    installed_by  BIGINT,
    installed_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_extensions IS '扩展包安装记录表';
COMMENT ON COLUMN bi.ba_extensions.ext_code      IS '扩展包编码';
COMMENT ON COLUMN bi.ba_extensions.ext_name      IS '扩展包名称';
COMMENT ON COLUMN bi.ba_extensions.version       IS '版本号';
COMMENT ON COLUMN bi.ba_extensions.pack_type     IS '包类型：declarative / jar / mixed';
COMMENT ON COLUMN bi.ba_extensions.manifest_json IS '扩展包清单内容';
COMMENT ON COLUMN bi.ba_extensions.jar_path      IS '插件 jar 路径';
COMMENT ON COLUMN bi.ba_extensions.assets_path   IS '前端资源目录';
COMMENT ON COLUMN bi.ba_extensions.entry_file    IS '前端入口文件';
COMMENT ON COLUMN bi.ba_extensions.sha256        IS '包摘要';
COMMENT ON COLUMN bi.ba_extensions.status        IS '状态：installed / enabled / disabled / error';
COMMENT ON COLUMN bi.ba_extensions.system_pack   IS '是否系统内置包（不可删除）';
COMMENT ON COLUMN bi.ba_extensions.owner_id      IS '归属人（为空表示系统内置）';
COMMENT ON COLUMN bi.ba_extensions.team_id       IS '归属团队（为空表示个人）';
COMMENT ON COLUMN bi.ba_extensions.visibility    IS '可见范围：personal / team / public';
COMMENT ON COLUMN bi.ba_extensions.publish_status IS '上架状态：draft / pending / approved / rejected';
COMMENT ON COLUMN bi.ba_extensions.reject_reason IS '审核驳回原因';
COMMENT ON COLUMN bi.ba_extensions.published_at  IS '审核通过时间';
COMMENT ON COLUMN bi.ba_extensions.error_msg     IS '安装或加载错误信息';
COMMENT ON COLUMN bi.ba_extensions.installed_by  IS '安装人';
COMMENT ON COLUMN bi.ba_extensions.installed_at  IS '安装时间';
COMMENT ON COLUMN bi.ba_extensions.updated_at    IS '更新时间';

CREATE TABLE IF NOT EXISTS bi.ba_extension_tools (
    id                BIGSERIAL     PRIMARY KEY,
    extension_id      BIGINT        NOT NULL,
    tool_code         VARCHAR(100)  NOT NULL,
    tool_key          VARCHAR(200)  NOT NULL,
    display_name      VARCHAR(200),
    description       VARCHAR(2000),
    tool_kind         VARCHAR(20)   NOT NULL,
    parameter_schema  VARCHAR(10000),
    binding_json      VARCHAR(5000),
    render_type       VARCHAR(200),
    risk_level        VARCHAR(20)   NOT NULL DEFAULT 'low',
    anonymous_allowed BOOLEAN       NOT NULL DEFAULT FALSE,
    requires_confirm  BOOLEAN       NOT NULL DEFAULT FALSE,
    enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_extension_tools IS '扩展包工具表';
COMMENT ON COLUMN bi.ba_extension_tools.extension_id      IS '所属扩展包标识';
COMMENT ON COLUMN bi.ba_extension_tools.tool_code         IS '包内工具编码';
COMMENT ON COLUMN bi.ba_extension_tools.tool_key          IS '全局工具键（包编码__工具编码）';
COMMENT ON COLUMN bi.ba_extension_tools.display_name      IS '工具显示名称';
COMMENT ON COLUMN bi.ba_extension_tools.description       IS '工具描述';
COMMENT ON COLUMN bi.ba_extension_tools.tool_kind         IS '工具类型：sql / http / render / export / jar';
COMMENT ON COLUMN bi.ba_extension_tools.parameter_schema  IS '参数 JSON Schema';
COMMENT ON COLUMN bi.ba_extension_tools.binding_json      IS '声明式绑定配置';
COMMENT ON COLUMN bi.ba_extension_tools.render_type       IS '前端渲染类型';
COMMENT ON COLUMN bi.ba_extension_tools.risk_level        IS '风险级别：low / medium / high';
COMMENT ON COLUMN bi.ba_extension_tools.anonymous_allowed IS '是否允许匿名调用';
COMMENT ON COLUMN bi.ba_extension_tools.requires_confirm  IS '是否需要用户确认';
COMMENT ON COLUMN bi.ba_extension_tools.enabled           IS '是否启用';
COMMENT ON COLUMN bi.ba_extension_tools.created_at        IS '创建时间';

CREATE TABLE IF NOT EXISTS bi.ba_extension_renderers (
    id               BIGSERIAL     PRIMARY KEY,
    extension_id     BIGINT        NOT NULL,
    render_type      VARCHAR(200)  NOT NULL,
    entry            VARCHAR(500),
    props_schema     VARCHAR(5000),
    supported_tools  VARCHAR(1000),
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_extension_renderers IS '扩展包前端渲染器表';
COMMENT ON COLUMN bi.ba_extension_renderers.extension_id    IS '所属扩展包标识';
COMMENT ON COLUMN bi.ba_extension_renderers.render_type     IS '渲染类型';
COMMENT ON COLUMN bi.ba_extension_renderers.entry           IS '前端入口文件';
COMMENT ON COLUMN bi.ba_extension_renderers.props_schema    IS '渲染参数 Schema';
COMMENT ON COLUMN bi.ba_extension_renderers.supported_tools IS '关联工具键列表';
COMMENT ON COLUMN bi.ba_extension_renderers.enabled         IS '是否启用';
COMMENT ON COLUMN bi.ba_extension_renderers.created_at      IS '创建时间';

-- ============================================================
-- 18. 智能体-扩展包关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_agent_extensions (
    id               BIGSERIAL     PRIMARY KEY,
    agent_id         BIGINT        NOT NULL,
    extension_id     BIGINT        NOT NULL,
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    config_overrides VARCHAR(5000),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_agent_extensions IS '智能体与扩展包关联表';
COMMENT ON COLUMN bi.ba_agent_extensions.agent_id         IS '智能体标识';
COMMENT ON COLUMN bi.ba_agent_extensions.extension_id     IS '扩展包标识';
COMMENT ON COLUMN bi.ba_agent_extensions.enabled          IS '是否启用';
COMMENT ON COLUMN bi.ba_agent_extensions.config_overrides IS '配置覆盖项';
COMMENT ON COLUMN bi.ba_agent_extensions.created_at       IS '创建时间';

-- ============================================================
-- 19. 工具调用审计表
-- ============================================================
CREATE TABLE IF NOT EXISTS bi.ba_tool_invocations (
    id              BIGSERIAL     PRIMARY KEY,
    conversation_id BIGINT,
    message_id      BIGINT,
    user_id         BIGINT,
    tool_key        VARCHAR(200)  NOT NULL,
    extension_code  VARCHAR(100),
    params_digest   VARCHAR(500),
    status          VARCHAR(20)   NOT NULL DEFAULT 'ok',
    duration_ms     BIGINT,
    error_msg       VARCHAR(2000),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  bi.ba_tool_invocations IS '工具调用审计表';
COMMENT ON COLUMN bi.ba_tool_invocations.conversation_id IS '对话标识';
COMMENT ON COLUMN bi.ba_tool_invocations.message_id      IS '消息标识';
COMMENT ON COLUMN bi.ba_tool_invocations.user_id         IS '调用用户标识';
COMMENT ON COLUMN bi.ba_tool_invocations.tool_key        IS '工具键';
COMMENT ON COLUMN bi.ba_tool_invocations.extension_code  IS '扩展包编码';
COMMENT ON COLUMN bi.ba_tool_invocations.params_digest   IS '参数摘要';
COMMENT ON COLUMN bi.ba_tool_invocations.status          IS '执行状态：ok / fail';
COMMENT ON COLUMN bi.ba_tool_invocations.duration_ms     IS '耗时（毫秒）';
COMMENT ON COLUMN bi.ba_tool_invocations.error_msg       IS '错误信息';
COMMENT ON COLUMN bi.ba_tool_invocations.created_at      IS '创建时间';

-- ============================================================
-- 20. 查询索引
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_ba_tool_invocations_conv ON bi.ba_tool_invocations (conversation_id);
CREATE INDEX IF NOT EXISTS idx_ba_tool_invocations_key ON bi.ba_tool_invocations (tool_key);
CREATE INDEX IF NOT EXISTS idx_ba_tool_invocations_user ON bi.ba_tool_invocations (user_id);
CREATE INDEX IF NOT EXISTS idx_ba_skills_owner ON bi.ba_skills (owner_id);
CREATE INDEX IF NOT EXISTS idx_ba_skills_publish ON bi.ba_skills (publish_status);
CREATE INDEX IF NOT EXISTS idx_ba_extensions_owner ON bi.ba_extensions (owner_id);
CREATE INDEX IF NOT EXISTS idx_ba_extensions_publish ON bi.ba_extensions (publish_status);
CREATE INDEX IF NOT EXISTS idx_ba_datasource_tables_ds ON bi.ba_datasource_tables (datasource_id);
CREATE INDEX IF NOT EXISTS idx_ba_datasource_columns_ds ON bi.ba_datasource_columns (datasource_id);
