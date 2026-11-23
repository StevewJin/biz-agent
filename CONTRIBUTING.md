# Contributing

**English** · [简体中文](CONTRIBUTING.zh-CN.md)

Thanks for your interest in BizAgent. This guide covers local setup, code conventions, and commit rules.

## 1. Prerequisites

| Dependency | Version |
|---|---|
| JDK | 22+ |
| Node.js | 20+ |
| PostgreSQL | 15+ |
| Maven | provided by `mvnw`, no separate install |

```bash
# Create database and schema
createdb bizagent
psql -d bizagent -f backend/schema.sql

# Backend (http://localhost:8080)
export BIZAGENT_DB_URL=jdbc:postgresql://localhost:5432/bizagent
export BIZAGENT_DB_USERNAME=postgres
export BIZAGENT_DB_PASSWORD=postgres
./backend/mvnw -f pom.xml -pl backend -am spring-boot:run

# Frontend (http://localhost:5173, /api and /ws already proxied)
cd frontend && npm install && npm run dev
```

Or simply `cp .env.example .env && docker compose up -d --build`.

## 2. One-time Setup

```bash
# After the first clone: enable the repository's pre-commit secret scan
./scripts/install-git-hooks.sh
```

The script only sets `git config core.hooksPath .githooks` and installs nothing external.
It blocks commits containing `.env`, `*.key`, `data/`, and suspicious secrets such as `sk-*`,
AWS/GitHub tokens, private keys, or JDBC URLs with inline credentials
(use `git commit --no-verify` for confirmed false positives).

## 3. Common Commands

```bash
# Backend: compile + run all unit tests
./backend/mvnw -f pom.xml -pl backend -am test

# Backend: run a single test class
./backend/mvnw -f pom.xml -pl backend -am test -Dtest=OutboundPolicyTest

# Frontend: type check + production build
cd frontend && npm run build

# Frontend: lint
cd frontend && npm run lint

# Full build (frontend bundled into the fat jar)
./build.sh
```

> On Windows, replace `./backend/mvnw` with `backend\mvnw.cmd`.

## 4. Conventions

**Backend**
- Packages: `agent` / `extension` / `service` / `controller` / `entity` / `repository` / `security` / `config`
- Do not add new "capabilities" to `backend`: package them as an extension pack (see [extension authoring guide](docs/extension-authoring.md))
- Data access: entities via JPA; business datasource queries must go through `ReadOnlySqlExecutor` (read-only, row and time limited)
- Any new outbound HTTP / SQL must pass `OutboundPolicy`
- Schema changes must be applied to both `backend/schema.sql` and `docker/postgres/init/01-schema.sql` (they must stay identical)

**Frontend**
- Use the design system in `src/components/admin/ui.tsx`; do not hand-roll styles per page
- Use `toast` / `confirmDialog` from `src/components/common/feedback.tsx` instead of `alert` / `confirm`
- Page components are route-split with `React.lazy` by default

**Comments and docs**
- Comments explain "why", not "what"; public APIs and security-relevant logic must be documented
- User-facing docs live in `docs/` and are indexed in `docs/README.md`

## 5. Commit Messages

Use semantic prefixes:

```
feat: add hot registration for extension renderers
fix: prevent restricted tools in anonymous sessions
docs: clarify the external driver directory
refactor: extract scope calculation from AgentRunPlanner
test: cover OutboundPolicy allow-list cases
chore: bump dependencies
```

- One PR does one thing
- Behavior changes must update the docs and `CHANGELOG.md`
- Make sure `mvnw test` and `npm run build` both pass before committing

## 6. Pull Requests

1. Fork the repo and branch from `main`: `feat/xxx`, `fix/xxx`
2. Implement and self-test, including error paths
3. Describe **context / changes / verification / impact** in the PR
4. For security-related changes (auth, SSRF, credentials, extension loading) also describe the threat model

## 7. Adding an Extension Pack

See the sample at [extensions-samples/chart-pro](extensions-samples/chart-pro):

```bash
cd extensions-samples/chart-pro
npm install
npm run build      # produces ui/entry.js
npm run pack       # produces dist/chart_pro.bpk
```

Import the `.bpk` from the admin console to verify, then commit the source along with the sample.

## 8. Code of Conduct

- Respect contributors of all experience levels; reviews focus on code and technical decisions
- Never commit real credentials, customer data, or internal hostnames
- Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md)
