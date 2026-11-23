# 更新日志

[English](CHANGELOG.md) · **简体中文**

本项目的所有重要变更都会记录在此文件。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-11-19

首个开源版本。

### 核心

- Agent Loop：规划 → 工具调用 → 结果回填 → 继续推理，流式输出（SSE over STOMP WebSocket），思考链与工具调用过程实时可见
- `AgentRunPlanner` 在每轮运行前生成 `AgentRunPlan`，明确系统提示词、工具白名单与数据源/接口作用域
- 匿名分享链接运行，自动剔除包含受限工具的技能
- 支持任意 OpenAI 兼容 LLM 端点，默认供应商与模型由配置驱动，可会话级覆盖

### 技能包

- Markdown 提示词 + `{{datasource:code}}` / `{{api:code}}` / `{{extension:code}}` / `{{tool:key}}` 占位符展开
- `resources_json` 声明数据源、接口、扩展包与工具白名单
- 表结构注入支持 `inline` / `lazy` 两种模式
- 三种触发方式：`auto` / `keyword` / `manual`
- 技能包导出、导入、归属控制（`owner_id`，内置技能仅管理员可改）

### 扩展包

- zip 包（`.bpk`）导入、版本升级、卸载，SHA-256 校验与文件落盘
- 四类声明式工具：`sql`（只读 + 命名参数 + 行数/超时限制）、`http`（仅登记接口 + 平台注入鉴权）、`render`（无副作用 `_render` 协议）、`export`（xlsx / csv）
- jar 代码插件：实现 `ExtensionTool` SPI，独立 ClassLoader 加载与卸载
- 前端扩展：IIFE 模块动态加载，共享宿主 React / Recharts，可注册任意 `_render` 渲染类型
- 内置扩展包：`builtin_db`、`builtin_http`、`builtin_chart`、`builtin_export`

### 业务系统接入

- 数据源管理：PostgreSQL / MySQL / Oracle，连接测试、表结构内省与缓存、运行时加载外部 JDBC 驱动
- 接口管理：URL / 方法 / 参数模板 / 鉴权配置（加密存储）/ 连通性测试
- 工具调用审计：`ba_tool_invocations` 记录工具、用户、会话、耗时与状态

### 安全

- JWT 认证与 `AccessGuard` 角色/归属校验
- 凭证 AES 加密落库，主密钥可持久化或由环境变量固定
- `OutboundPolicy` SSRF 防护：拦截回环、内网、云元数据地址，支持白名单
- 工具白名单默认只注入低风险工具；匿名会话禁止调用受限工具
- CORS 可配置、出站 URL 参数编码、日志默认不落原始报文

### 技能包与扩展包归属 / 上架

- 两者统一归属模型：`owner_id` + `team_id` + `visibility`(personal/team/public) + `publish_status`(draft/pending/approved/rejected)
- 声明式扩展包对所有用户开放导入（含 jar 的代码包仍仅管理员）；导入后归属个人或当前团队
- 新增上架审核流程：归属人申请上架 → 管理员在「上架审核」通过/驳回（可填原因）→ 归属人可撤回/下架
- 可见性同时约束列表、详情、工具清单、`{{extension:code}}` 占位符解析与智能体绑定
- 前端新增「上架审核」菜单与状态徽标（未上架 / 待审核 / 已上架 / 已驳回）

### 对话体验

- 工具调用卡片改为展示具体信息：工具展示名（扩展包 manifest 的 displayName）、工具类型（SQL 查询 / 接口调用 / 渲染输出 / 文件导出 / 代码插件）、所属扩展包、触发它的技能包与工具键，不再显示「正在查询业务系统」这类泛化文案
- 上述元数据同时写入思维链（`ba_thinking_chain_entries` 新增 `tool_display_name` / `extension_code` / `extension_name` / `tool_kind` / `skill_name`），历史会话回看同样可见

### 安全

- 新增启动守卫 `StartupSecurityGuard`：JWT 仍为仓库默认值或本次启动新生成 AES 主密钥时，`prod` profile 拒绝启动，其他环境打印醒目告警
- 新增零依赖提交前密钥扫描 `.githooks/pre-commit` 与安装脚本 `scripts/install-git-hooks.sh`
- SECURITY.md 补充仓库安全设置与凭证误提交后的处置流程

### 文档

- 全部文档改为英文优先（`README.md`、`CONTRIBUTING.md`、`SECURITY.md`、`CHANGELOG.md`、`docs/*`），原中文版本保留为 `*.zh-CN.md`，每篇顶部提供语言切换

### 工程化

- Maven 多模块：`extension-api`（SPI）+ `backend`
- 后端单元测试（引擎参数校验、只读 SQL 校验、出站策略、权限校验）
- `Dockerfile` + `docker-compose.yml`，`postgres` 首启自动建表
- GitHub Actions CI（后端测试 + 前端构建）
- `/actuator/health` 健康检查
