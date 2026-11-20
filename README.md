# BizAgent · 业务执行智能体平台

一个面向企业业务场景的轻量级 AI Agent 平台：**一个 Agent Loop + 技能包系统 + 扩展包系统**。

平台本身不硬编码任何业务能力。所有「能做什么」都由**技能包**（提示词 + 资源引用）和**扩展包**（工具实现 + 前端渲染器）在运行时注入，因此可以把一个通用 Agent 快速改造成懂你业务的数据分析助手、报表助手或流程助手。

许可证：[MIT](LICENSE)

---

## 核心特性

**Agent Loop**
- 单一推理循环：规划 → 工具调用 → 结果回填 → 继续推理，最多 `app.agent.max-turns` 轮
- 流式输出（SSE over STOMP WebSocket），思考链与工具调用过程实时可见
- 每轮运行由 `AgentRunPlanner` 生成 `AgentRunPlan`，明确本轮可用的系统提示词、工具白名单与数据源/接口作用域
- 匿名分享链接运行，自动剔除含受限工具的技能

**技能包（Skill Pack）**
- 技能 = Markdown 提示词 + 资源引用 + 工具白名单
- 提示词支持占位符展开：`{{datasource:code}}`、`{{api:code}}`、`{{extension:code}}`、`{{tool:key}}`
- 表结构注入支持 `inline`（内联字段）与 `lazy`（仅表名，按需查询）两种模式，控制 token 消耗
- 触发方式：`auto`（模型自主）/ `keyword`（关键词命中）/ `manual`（仅手动）
- 归属控制：个人创建的技能仅本人与管理员可改

**扩展包（Extension Pack）**
- 统一以 zip 包（`.bpk`）导入，支持版本升级与卸载
- 四类**声明式工具**，无需写代码：
  - `sql`：只读查询，命名参数 `#{name}`，自动 `search_path`、行数与超时限制
  - `http`：只能调用「业务接口」中登记的端点，鉴权由平台注入，模型与扩展包都拿不到明文凭证
  - `render`：无副作用，直接把参数作为前端渲染 props
  - `export`：生成 xlsx / csv 下载卡片
- **代码插件**：jar 内实现 `ExtensionTool` SPI，独立 ClassLoader 加载，卸载即回收
- **前端扩展**：预编译 IIFE 模块动态加载，共享宿主 React / Recharts，可注册任意 `_render` 类型（图表、看板、自定义视图等）

**业务系统接入**
- 数据源管理：PostgreSQL / MySQL / Oracle 等，支持运行时加载外部 JDBC 驱动 jar；表结构自动内省与缓存
- 接口管理：登记业务系统 API，支持参数模板、鉴权配置加密存储、连通性测试
- 所有 SQL / HTTP 调用落审计表 `ba_tool_invocations`（工具、用户、会话、耗时、状态）

**安全基线**
- JWT 认证 + 角色/归属校验（`AccessGuard`）
- 凭证以 AES 加密落库，主密钥可持久化或由环境变量固定
- SSRF 防护：拦截回环地址、内网地址、云元数据端点，内网白名单显式放行
- 工具白名单默认只注入低风险工具；匿名会话禁止调用受限工具
- 出站 URL 参数编码、导出目录受控、日志默认不落原始请求体

---

## 架构

```
                    ┌──────────────────────────────┐
   浏览器            │  React 19 + Vite + Tailwind  │
   (聊天 / 管理台)    │  扩展渲染器运行时              │
                    └───────────┬──────────────────┘
                      REST / WS │ (STOMP)
                    ┌───────────▼──────────────────┐
                    │  Spring Boot 3.3 (Java 22)   │
                    │                              │
                    │  AgentLoop ── AgentRunPlanner│
                    │      │              │        │
                    │      │        SkillRegistry   │
                    │      │        (占位符 → 提示词) │
                    │      ▼              │        │
                    │  ToolCatalog ◄──────┘        │
                    │      │                       │
                    │  ExtensionRuntime            │
                    │   ├─ SqlToolEngine  ─┐       │
                    │   ├─ HttpToolEngine ─┤       │
                    │   ├─ RenderToolEngine│       │
                    │   └─ ExportToolEngine│       │
                    └──────┬───────────────┴───────┘
                           │
              ┌────────────┴─────────────┐
              ▼                          ▼
      PostgreSQL (bi schema)      业务系统 DB / API
```

- **`extension-api`**：扩展包 SPI，代码插件只依赖它
- **`backend`**：Agent 编排、技能包、扩展包运行时、数据源/接口管理、认证授权
- **`frontend`**：对话界面 + 管理控制台 + 扩展渲染器宿主

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.3、Spring Web / WebFlux、Spring Data JPA、Spring Security、Spring WebSocket（STOMP）、JJWT |
| 数据库 | PostgreSQL 15+（schema 默认 `bi`）、Spring Data JPA、HikariCP |
| 前端 | React 19、TypeScript、Vite、Tailwind CSS、Zustand、Recharts、react-markdown |
| 构建 | Maven 多模块（`extension-api` + `backend`）、npm |
| 运行 | 单体 fat jar（前端产物并入静态资源）或 Docker Compose |

---

## 目录结构

```
.
├── extension-api/              # 扩展包 SPI（ExtensionTool / ExtensionContext / SqlExecutor / HttpExecutor）
├── backend/
│   ├── schema.sql              # 完整建表脚本（PostgreSQL）
│   └── src/main/
│       ├── java/fun/jinwei/ai/biz/
│       │   ├── agent/          # AgentLoop、AgentRunPlanner
│       │   ├── extension/      # 运行时、安装器、声明式引擎、内置扩展包清单
│       │   ├── service/        # 技能包、数据源、接口、会话、LLM
│       │   ├── controller/     # REST 接口
│       │   ├── security/       # AccessGuard、OutboundPolicy
│       │   └── entity/         # JPA 实体
│       └── resources/
│           ├── application.yml
│           └── extensions/builtin/   # 内置扩展包（db / http / chart / export）
├── frontend/
│   └── src/
│       ├── extensions/         # 前端扩展宿主与渲染器注册表
│       ├── components/         # 聊天组件 + 管理端 UI 组件库
│       └── pages/              # 对话、技能市场、管理控制台
├── extensions-samples/
│   └── chart-pro/              # 扩展示例包（前端渲染器 + 打包脚本）
├── docker/postgres/init/       # 容器首次启动自动建表
├── docs/                       # 开发文档
├── Dockerfile / docker-compose.yml
├── build.sh                    # 一键打包（前端产物并入 fat jar）
└── start.sh                    # 启动脚本（读取 .env）
```

---

## 快速开始

### 前置条件

- JDK 22+（构建与运行）
- Node.js 20+
- PostgreSQL 15+

### 方式一：Docker Compose（最快）

```bash
cp .env.example .env      # 填入 DEEPSEEK_API_KEY 等
docker compose up -d --build
```

访问 <http://localhost:8080>。`postgres` 容器首次启动会自动执行 `docker/postgres/init/01-schema.sql` 建表。

### 方式二：本地开发

```bash
# 1. 建表
createdb bizagent
psql -d bizagent -f backend/schema.sql

# 2. 后端
export BIZAGENT_DB_URL=jdbc:postgresql://localhost:5432/bizagent
export BIZAGENT_DB_USERNAME=postgres
export BIZAGENT_DB_PASSWORD=postgres
export DEEPSEEK_API_KEY=sk-xxxx
./backend/mvnw -f pom.xml -pl backend -am spring-boot:run

# 3. 前端（另开终端）
cd frontend && npm install && npm run dev    # http://localhost:5173，/api 与 /ws 已代理到 8080
```

### 生产打包（前端并入单个 jar）

```bash
./build.sh          # 产物 backend/target/backend-1.0.0.jar
./start.sh          # 读取项目根目录 .env
```

### 首个管理员

系统不内置账号，注册后提升角色，重新登录生效：

```sql
UPDATE bi.ba_users SET role_type = 'admin' WHERE username = 'your-name';
```

管理员可导入 jar 代码插件、修改系统内置技能包、管理他人创建的数据源/接口/智能体。

---

## 配置

所有配置均可用环境变量覆盖，完整清单见 [`.env.example`](.env.example)。

| 变量 | 说明 | 默认值 |
|---|---|---|
| `BIZAGENT_DB_URL` / `_USERNAME` / `_PASSWORD` / `_DRIVER` | 平台主库连接 | `jdbc:postgresql://localhost:5432/bizagent` |
| `BIZAGENT_JWT_SECRET` | JWT 签名密钥，Base64，解码后 ≥ 64 字节 | 内置开发默认值（生产必改） |
| `BIZAGENT_MASTER_KEY` | AES 主密钥，加密 LLM Key / 数据源密码 / 接口鉴权 | 留空则生成 `data/master.key` |
| `BIZAGENT_CORS_ORIGINS` | 允许跨域的前端地址，逗号分隔 | 空（仅同源） |
| `BIZAGENT_OUTBOUND_HOSTS` | 接口调用放行的内网主机白名单 | 空（内网全禁） |
| `BIZAGENT_DATA_DIR` / `BIZAGENT_EXT_HOME` / `BIZAGENT_DRIVER_DIR` | 数据 / 扩展包 / 外部驱动目录 | `./data/**` |
| `APP_EXPORT_DIR` | 导出文件目录（不可写时回退临时目录） | `./data/exports` |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | 默认 LLM 供应商（任意 OpenAI 兼容端点） | `https://api.deepseek.com` / `deepseek-flash` |
| `BIZAGENT_LOG_LEVEL` / `BIZAGENT_LOG_RAW_PAYLOAD` | 应用日志级别、是否打印完整 LLM 报文 | `INFO` / `false` |

> `BIZAGENT_MASTER_KEY` 与 `data/master.key` 必须离线备份。主密钥丢失后，库内所有加密凭证不可解，只能重新录入。

---

## 权限模型

平台按 SaaS 常见三层角色划分，**每一个功能模块都有对应的权限校验**：

| 模块 | 系统管理员 | 团队负责人 / 团队管理员 | 普通成员 |
|---|---|---|---|
| LLM 供应商 | 增删改查 | — | — |
| 扩展包 | 全部 + 上架审核 | 本人/本团队创建的包可导入、启停、卸载、申请上架 | 同左（含 jar 的包仅管理员可导入） |
| 技能包 | 全部 + 上架审核 | 本人/本团队创建的技能可增删改、申请上架 | 同左 |
| 数据源 / 接口 | 全部 | 本人 + 本团队 | 本人 + 本团队（只读） |
| 智能体 | 全部 | 本人 + 本团队（可改本团队） | 本人 + 本团队（只读） |
| 团队 | 全部 | 本人负责的团队可改成员 | 仅查看所在团队 |
| 会话记录 | 全平台 | 本人 + 本团队 | 仅本人 |
| 导出文件 | 全部 | 本人会话 / 本人智能体会话 | 本人会话 |
| 测试对话 | ✅ | ✅ | ✅ |

### 归属与上架（技能包 / 扩展包）

两者共用一套模型：`owner_id` + `team_id` + `visibility`(personal/team/public) + `publish_status`(draft/pending/approved/rejected)。

| 状态 | 含义 | 谁可见 |
|---|---|---|
| `draft` | 未上架 | 归属人、团队管理者、系统管理员 |
| `pending` | 已提交上架，等审核 | 同上（管理员在「上架审核」处理） |
| `approved` | 审核通过 | **全平台所有用户** |
| `rejected` | 审核驳回 | 归属人可见驳回原因，可修改后重新提交 |

流程：归属人「申请上架」→ 管理员在「上架审核」通过/驳回（驳回需填原因）→ 归属人可随时「撤回/下架」。
`owner_id` 为空的系统内置包属于平台能力，全平台可用且仅管理员可改。

实现要点：

- 所有列表接口都按归属过滤（`owner_id` / `team_id`），不会返回无权数据
- 扩展包：声明式包所有用户可导入；含 jar 代码的包等价服务端任意代码，仅管理员可导入
- 工具清单、`{{extension:code}}` 占位符、智能体绑定扩展包三处都会校验扩展包可见性
- 团队上下文（`X-Data-Context`）会校验成员身份，**伪造团队 ID 直接 403**
- 前端通过 `GET /api/capabilities` 获取 `admin / managedTeamIds / teams`，据此隐藏菜单、路由与按钮（`useCapabilities` 钩子 + `RequireAdmin` 守卫）
- 前端隐藏只是体验优化，**真正的越权拦截始终在后端接口**

---

## 使用流程

1. **接入业务系统**：管理控制台 → 数据源，填写 JDBC 连接并测试；系统自动内省表与字段
2. **登记接口**：管理控制台 → 接口，配置 URL、方法、参数模板与鉴权（加密存储）
3. **导入扩展包**：管理控制台 → 扩展包，上传 `.bpk` zip；内置 `db / http / chart / export` 已随包提供
4. **编排技能包**：管理控制台 → 技能，写 Markdown 提示词，用 `{{datasource:xxx}}` 等占位符引用资源，选择可用工具
5. **绑定智能体**：为智能体选择技能包与扩展包，即可在对话页使用
6. **分享**：生成分享链接，匿名访问时自动降级为只读能力

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/README.md](docs/README.md) | 文档索引 |
| [docs/extension-authoring.md](docs/extension-authoring.md) | 扩展包开发指南（manifest、四类声明式工具、jar 插件、前端渲染器） |
| [docs/skill-authoring-guide.md](docs/skill-authoring-guide.md) | 技能包编写指南（占位符、触发方式、表结构注入模式） |
| [docs/database-setup.md](docs/database-setup.md) | 建表、环境变量、容器化与容量规划 |
| [extensions-samples/chart-pro](extensions-samples/chart-pro) | 可直接导入的扩展示例包 |

---

## 安全提示

- 本仓库**不包含任何真实凭证**；`application.yml` 中的默认值仅用于本地开发
- 生产部署务必设置 `BIZAGENT_JWT_SECRET`、`BIZAGENT_MASTER_KEY`、`BIZAGENT_CORS_ORIGINS`
- 扩展包 jar 拥有数据库访问与出站 HTTP 能力，**仅授予可信来源**，导入前请审计代码
- 提交代码前请执行 `./scripts/install-git-hooks.sh` 启用密钥扫描钩子（详见 [SECURITY.md](SECURITY.md)）
- 启动时会检查安全配置：`prod` profile 下若仍使用仓库默认 JWT 密钥会直接拒绝启动
- 发现安全问题请参考 [SECURITY.md](SECURITY.md)，不要直接开公开 issue

---

## 贡献

欢迎提交 Issue 与 Pull Request，请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

[MIT](LICENSE)
