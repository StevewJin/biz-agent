# 数据库与部署

[English](database-setup.md) · **简体中文**

## 一、初始化表结构

BizAgent 的表结构由 **`backend/schema.sql` 单一维护**：JPA 为 `ddl-auto=none`，应用启动不会改表；脚本自身包含 `CREATE SCHEMA IF NOT EXISTS bi;`，所有语句都是 `IF NOT EXISTS` 形式，可反复执行。

```bash
createdb bizagent
psql -h <host> -U <user> -d bizagent -f backend/schema.sql
```

| 项目 | 值 |
|---|---|
| schema | `bi` |
| 表前缀 | `ba_` |
| 建表脚本 | `backend/schema.sql` |
| 容器初始化脚本 | `docker/postgres/init/01-schema.sql`（内容与上面完全一致，`postgres` 容器首次启动自动执行） |

修改表结构时，两份文件必须同步更新。

## 二、环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `BIZAGENT_DB_URL` | 主库连接串 | `jdbc:postgresql://localhost:5432/bizagent` |
| `BIZAGENT_DB_USERNAME` / `BIZAGENT_DB_PASSWORD` | 主库账号 | `bizagent` |
| `BIZAGENT_DB_DRIVER` | 主库驱动类 | `org.postgresql.Driver` |
| `BIZAGENT_MASTER_KEY` | AES 主密钥（Base64/Hex，32 字节） | 留空则生成 `data/master.key` |
| `BIZAGENT_DATA_DIR` | 运行数据目录 | `./data` |
| `BIZAGENT_EXT_HOME` | 扩展包安装目录 | `./data/extensions` |
| `BIZAGENT_DRIVER_DIR` | 外部 JDBC 驱动目录 | `./data/drivers` |
| `BIZAGENT_JWT_SECRET` | JWT 签名密钥（Base64，解码后 ≥ 64 字节） | 内置开发默认值，生产必改 |
| `BIZAGENT_CORS_ORIGINS` | 允许跨域的前端地址，逗号分隔 | 空（仅同源） |
| `BIZAGENT_OUTBOUND_HOSTS` | 接口调用放行的内网主机白名单 | 空（内网全禁） |
| `APP_EXPORT_DIR` | 导出目录 | `./data/exports`，不可写时回退临时目录 |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | 默认 LLM 供应商 | `https://api.deepseek.com` / `deepseek-flash` |
| `BIZAGENT_LOG_LEVEL` | 应用日志级别 | `INFO` |
| `BIZAGENT_LOG_RAW_PAYLOAD` | 是否打印完整 LLM 报文 | `false` |

> 主密钥务必离线备份。轮换主密钥需要重新录入所有 LLM API Key、数据源密码与接口鉴权配置。

## 三、管理员账号

系统不内置账号。注册后提升角色，重新登录生效（JWT 内携带角色）：

```sql
UPDATE bi.ba_users SET role_type = 'admin' WHERE username = 'your-name';
```

管理员能力：

- 导入 / 卸载扩展包（含 jar 代码插件）
- 修改系统内置技能包（`owner_id IS NULL`）
- 管理他人创建的数据源、接口、智能体

## 四、本地运行

```bash
# JDK 22+；也可将 JDK 解压到项目根目录（jdk-*），脚本会自动使用
export JAVA_HOME=/path/to/jdk-22
./backend/mvnw -f pom.xml -pl backend -am package -DskipTests
java -jar backend/target/backend-1.0.0.jar
```

前端开发模式：

```bash
cd frontend && npm install && npm run dev   # 5173，/api 与 /ws 代理到 8080
```

生产打包（前端产物并入 jar）：

```bash
./build.sh
./start.sh          # 读取项目根目录 .env
```

## 五、容器化

```bash
cp .env.example .env
docker compose up -d --build
```

- `postgres` 首次启动执行 `docker/postgres/init/01-schema.sql`
- `app` 挂载 `./data` 持久化主密钥、扩展包与导出文件
- 健康检查：`GET /actuator/health`

## 六、容量与数据保留

- 工具审计写在 `ba_tool_invocations`，按合规要求定期清理或归档
- 思维链条目 `ba_thinking_chain_entries` 增长最快，可按 `created_at` 分区或定期删除
- 数据源表结构缓存（`ba_datasource_tables` / `ba_datasource_columns`）在「同步表结构」时全量刷新
- 扩展包升级：导入同 `code` 的新版本即可，旧版本目录自动清理
