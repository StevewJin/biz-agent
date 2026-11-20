# 贡献指南

感谢你愿意为 BizAgent 做贡献。本文说明本地环境、代码约定与提交规范。

## 一、环境准备

| 依赖 | 版本 |
|---|---|
| JDK | 22+ |
| Node.js | 20+ |
| PostgreSQL | 15+ |
| Maven | 随仓库 `mvnw` 提供，无需单独安装 |

```bash
# 建库建表
createdb bizagent
psql -d bizagent -f backend/schema.sql

# 后端（http://localhost:8080）
export BIZAGENT_DB_URL=jdbc:postgresql://localhost:5432/bizagent
export BIZAGENT_DB_USERNAME=postgres
export BIZAGENT_DB_PASSWORD=postgres
./backend/mvnw -f pom.xml -pl backend -am spring-boot:run

# 前端（http://localhost:5173，已配置 /api 与 /ws 代理）
cd frontend && npm install && npm run dev
```

也可以直接 `cp .env.example .env && docker compose up -d --build`。

## 二、初始化与本地产物

```bash
# 首次克隆后：启用仓库内置的提交前检查（密钥扫描）
./scripts/install-git-hooks.sh
```

该脚本只是设置 `git config core.hooksPath .githooks`，不安装任何外部依赖。
提交时会自动拦截 `.env`、`*.key`、`data/` 等敏感文件，以及 `sk-*`、AWS/GitHub Token、
私钥、带口令的 JDBC 连接串等疑似密钥（误报可用 `git commit --no-verify` 跳过）。

## 三、常用命令

```bash
# 后端：编译 + 全部单元测试
./backend/mvnw -f pom.xml -pl backend -am test

# 后端：只跑某个测试类
./backend/mvnw -f pom.xml -pl backend -am test -Dtest=OutboundPolicyTest

# 前端：类型检查 + 生产构建
cd frontend && npm run build

# 前端：lint
cd frontend && npm run lint

# 全量打包（前端产物并入 fat jar）
./build.sh
```

> Windows 用户把 `./backend/mvnw` 换成 `backend\mvnw.cmd`。

## 四、代码约定

**后端**
- 包结构：`agent` / `extension` / `service` / `controller` / `entity` / `repository` / `security` / `config`
- 新增「能力」不要写进 `backend`：按 [扩展包开发指南](docs/extension-authoring.md) 做成扩展包
- 数据访问：实体走 JPA；业务系统数据源查询必须经过 `ReadOnlySqlExecutor`（只读、限行、限时）
- 任何新增的对外 HTTP / SQL 出口都要经过 `OutboundPolicy` 校验
- 变更表结构时同步更新 `backend/schema.sql` 与 `docker/postgres/init/01-schema.sql`（两份必须一致）

**前端**
- 统一使用 `src/components/admin/ui.tsx` 中的设计系统组件，避免各页面各写一套样式
- 反馈交互用 `src/components/common/feedback.tsx` 的 `toast` / `confirmDialog`，不要用 `alert` / `confirm`
- 页面组件默认 `React.lazy` 按路由分包

**注释与文档**
- 注释写「为什么」，不写「是什么」；公共 API 与安全相关逻辑必须有说明
- 面向使用者的文档放 `docs/`，并在 `docs/README.md` 建立索引

## 五、提交规范

采用语义化前缀：

```
feat: 新增扩展包渲染器热注册
fix: 修复匿名会话仍可调用受限工具
docs: 补充数据源驱动目录说明
refactor: 拆分 AgentRunPlanner 的作用域计算
test: 补充 OutboundPolicy 白名单用例
chore: 升级依赖
```

- 一个 PR 只做一件事，改动尽量聚焦
- 涉及行为变更请同步更新文档与 `CHANGELOG.md`
- 提交前确保 `mvnw test` 与 `npm run build` 均通过

## 六、提交 Pull Request

1. Fork 本仓库并从 `main` 切出分支：`feat/xxx`、`fix/xxx`
2. 完成改动并自测（包含异常路径）
3. 在 PR 描述中说明：**背景 / 改动内容 / 验证方式 / 影响面**
4. 涉及安全相关改动（鉴权、SSRF、凭证、扩展包加载）请额外说明威胁模型

## 七、新增扩展包

示例包见 [extensions-samples/chart-pro](extensions-samples/chart-pro)。开发流程：

```bash
cd extensions-samples/chart-pro
npm install
npm run build      # 产出 ui/entry.js
npm run pack       # 产出 dist/chart_pro.bpk
```

在「管理控制台 → 扩展包」导入 `.bpk` 验证效果，再把源码一并提交到 `extensions-samples/`。

## 八、行为准则

- 尊重不同经验水平的贡献者，评审聚焦代码与技术决策
- 不提交任何真实凭证、客户数据或内部系统地址
- 发现安全漏洞请按 [SECURITY.md](SECURITY.md) 私下报告
