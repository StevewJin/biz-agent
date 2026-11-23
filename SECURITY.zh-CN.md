# 安全策略

[English](SECURITY.md) · **简体中文**

## 报告漏洞

**请不要通过公开 Issue 披露安全问题。**

请通过以下方式私下报告：

- 邮件：steve.w.jin@gmail.com
- 或在 GitHub 仓库的 **Security → Report a vulnerability** 中提交私有报告

报告请尽量包含：

1. 受影响的版本 / 提交号
2. 漏洞类型与影响（如越权、SSRF、凭证泄露、任意代码执行）
3. 复现步骤或最小 PoC
4. 你评估的严重程度

我们会在 **5 个工作日内**确认收到，并在修复发布后致谢（如需匿名请说明）。

## 支持范围

| 版本 | 安全更新 |
|---|---|
| `main` 分支 | ✅ |

## 仓库与提交安全

本仓库**不包含任何真实凭证**：数据库连接、LLM API Key、JWT 密钥、AES 主密钥全部通过环境变量注入（见 `.env.example`），`.env`、`data/`、`*.key` 均已被 `.gitignore` 排除。

三层防护：

| 层级 | 机制 | 位置 |
|---|---|---|
| 交付时 | 启动守卫：JWT 仍为仓库默认值时，`prod` profile 直接拒绝启动；本次启动新生成 AES 主密钥同样拒绝启动（开发环境仅打印醒目告警） | `config/StartupSecurityGuard` |
| 提交前 | 密钥扫描：拦截 `.env` / `*.key` / `data/` 等敏感文件与 `sk-*`、AWS/GitHub Token、私钥、带口令的 JDBC 串 | `.githooks/pre-commit` |
| 托管平台 | Secret scanning + Push protection，push 阶段再拦一次 | GitHub 仓库设置 |

首次克隆后启用本地检查：

```bash
./scripts/install-git-hooks.sh
```

GitHub 仓库创建后建议开启（Settings → Code security）：

- **Secret scanning**：自动识别已提交的密钥并告警
- **Push protection**：在 push 阶段直接拦截已知格式的密钥
- **Dependabot alerts**：依赖漏洞提醒

### 凭证误提交后的处置顺序

1. **先轮换**：到对应平台吊销并重建（LLM API Key、数据库口令、JWT 密钥），同步更新部署环境变量
2. 再清理历史：`git filter-repo --path <file> --invert-paths` 后强制推送
3. 通知协作者重新克隆（旧克隆仍带历史）
4. 复盘使用痕迹：API 用量、数据库登录日志、异常请求

> 顺序不能反：历史清理完，泄漏的密钥依然有效。另需注意 fork、镜像与搜索引擎缓存无法回收，这也是「先轮换」的原因。

## 部署方需自行承担的安全责任

BizAgent 是一个可执行任意扩展包 + 直连业务数据库的平台，**部署方必须**：

1. **固定密钥**
   - 设置 `BIZAGENT_JWT_SECRET`（Base64，解码后 ≥ 64 字节）
   - 设置 `BIZAGENT_MASTER_KEY` 并离线备份；丢失后库内加密凭证不可解
   - 不要使用 `application.yml` 中的开发默认值上线

2. **限制跨域与出站**
   - `BIZAGENT_CORS_ORIGINS` 只填可信前端域名，不要使用通配符
   - 默认禁止访问内网与回环地址；确需访问内网接口时用 `BIZAGENT_OUTBOUND_HOSTS` 精确放行
   - 云环境务必保持元数据地址（`169.254.169.254`）不可达

3. **审计扩展包**
   - jar 代码插件拥有数据库访问与出站请求能力，等价于服务端任意代码
   - 只导入可信来源；导入前检查 `manifest.json`、工具声明与依赖
   - 生产环境如需收紧，可关闭 `app.extension.allow-code-packs`

4. **最小权限数据库账号**
   - 业务数据源账号应只读，且只授权必要的 schema / 表
   - 平台侧只读校验无法替代数据库层权限

5. **边界防护**
   - 在公网部署时置于反向代理之后，启用 HTTPS、限流与访问日志
   - 不要向公网暴露 PostgreSQL 端口与 `/actuator` 之外的端点

## 已知设计边界

- 声明式 SQL 工具只允许单条 `SELECT` / `WITH` 语句，但**不限制结果语义**；敏感数据需靠数据库权限控制
- `render` 工具无副作用，但扩展包前端代码运行在宿主页面上下文，可按需 CSP 收紧
- 工具审计表 `ba_tool_invocations` 记录参数摘要，请按数据合规要求设置保留周期
