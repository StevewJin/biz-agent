# Database and Deployment

**English** · [简体中文](database-setup.zh-CN.md)

## 1. Creating the Schema

The schema is maintained in **`backend/schema.sql` only**: JPA runs with `ddl-auto=none` and never alters tables at startup. The script starts with `CREATE SCHEMA IF NOT EXISTS bi;` and every statement uses `IF NOT EXISTS`, so it is safe to re-run.

```bash
createdb bizagent
psql -h <host> -U <user> -d bizagent -f backend/schema.sql
```

| Item | Value |
|---|---|
| Schema | `bi` |
| Table prefix | `ba_` |
| DDL script | `backend/schema.sql` |
| Container init script | `docker/postgres/init/01-schema.sql` (identical content, executed on first `postgres` container start) |

Both files must be updated together whenever the schema changes.

## 2. Environment Variables

| Variable | Description | Default |
|---|---|---|
| `BIZAGENT_DB_URL` | Main database URL | `jdbc:postgresql://localhost:5432/bizagent` |
| `BIZAGENT_DB_USERNAME` / `BIZAGENT_DB_PASSWORD` | Main database credentials | `bizagent` |
| `BIZAGENT_DB_DRIVER` | JDBC driver class | `org.postgresql.Driver` |
| `BIZAGENT_MASTER_KEY` | AES master key (Base64/Hex, 32 bytes) | empty → generates `data/master.key` |
| `BIZAGENT_DATA_DIR` | Runtime data directory | `./data` |
| `BIZAGENT_EXT_HOME` | Extension install directory | `./data/extensions` |
| `BIZAGENT_DRIVER_DIR` | External JDBC driver directory | `./data/drivers` |
| `BIZAGENT_JWT_SECRET` | JWT signing key (Base64, ≥ 64 bytes decoded) | built-in dev default, must change in production |
| `BIZAGENT_CORS_ORIGINS` | Allowed frontend origins, comma separated | empty (same origin only) |
| `BIZAGENT_OUTBOUND_HOSTS` | Allow-list of private hosts for API tools | empty (all private hosts blocked) |
| `APP_EXPORT_DIR` | Export directory | `./data/exports`, falls back to temp dir |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | Default LLM provider | `https://api.deepseek.com` / `deepseek-flash` |
| `BIZAGENT_LOG_LEVEL` | Application log level | `INFO` |
| `BIZAGENT_LOG_RAW_PAYLOAD` | Whether to dump raw LLM payloads | `false` |

> Back up the master key offline. Rotating it requires re-entering every LLM API key, datasource password, and API auth config.

## 3. Administrator Account

There is no built-in account. Register, then promote the account; the role takes effect on the next login (it is embedded in the JWT):

```sql
UPDATE bi.ba_users SET role_type = 'admin' WHERE username = 'your-name';
```

Administrators can:

- Import / uninstall extension packs (including jar code plugins)
- Edit builtin skill packs (`owner_id IS NULL`)
- Manage datasources, APIs, and agents created by others

## 4. Running Locally

```bash
# JDK 22+; you may also unpack a JDK into the project root (jdk-*) and the scripts will pick it up
export JAVA_HOME=/path/to/jdk-22
./backend/mvnw -f pom.xml -pl backend -am package -DskipTests
java -jar backend/target/backend-1.0.0.jar
```

Frontend in dev mode:

```bash
cd frontend && npm install && npm run dev   # 5173, /api and /ws proxied to 8080
```

Production build (frontend bundled into the jar):

```bash
./build.sh
./start.sh          # reads .env from the project root
```

## 5. Containers

```bash
cp .env.example .env
docker compose up -d --build
```

- `postgres` executes `docker/postgres/init/01-schema.sql` on first start
- `app` mounts `./data` to persist the master key, extension packs, and exports
- Health check: `GET /actuator/health`

## 6. Capacity and Data Retention

- Tool audits are stored in `ba_tool_invocations`; archive or prune them according to your compliance policy
- `ba_thinking_chain_entries` grows the fastest; consider partitioning by `created_at` or periodic deletion
- The datasource schema cache (`ba_datasource_tables` / `ba_datasource_columns`) is fully refreshed on "Sync schema"
- Upgrading an extension pack: import a new version with the same `code`; the old version directory is cleaned up automatically
