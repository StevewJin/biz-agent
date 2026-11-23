# Changelog

**English** · [简体中文](CHANGELOG.zh-CN.md)

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-19

First open-source release.

### Core

- Agent Loop: plan → tool call → feed results back → continue, with streaming output (SSE over STOMP WebSocket); thinking chain and tool calls are visible in real time
- `AgentRunPlanner` produces an `AgentRunPlan` per run, pinning the system prompt, tool whitelist, and datasource/API scope
- Anonymous share links, automatically dropping skills that contain restricted tools
- Any OpenAI-compatible LLM endpoint; the default provider and model are configuration-driven and can be overridden per conversation

### Skill Packs

- Markdown prompts with `{{datasource:code}}` / `{{api:code}}` / `{{extension:code}}` / `{{tool:key}}` expansion
- `resources_json` declares datasources, APIs, extension packs, and the tool whitelist
- Schema injection supports `inline` / `lazy`
- Three trigger modes: `auto` / `keyword` / `manual`
- Export, import, and ownership control (`owner_id`; builtin skills are admin-only)

### Extension Packs

- Zip (`.bpk`) import, version upgrade, uninstall; SHA-256 verification and on-disk storage
- Four declarative tool kinds: `sql` (read-only + named parameters + row/timeout limits), `http` (registered endpoints only + platform-injected auth), `render` (side-effect-free `_render` protocol), `export` (xlsx / csv)
- Jar code plugins: implement the `ExtensionTool` SPI, loaded and unloaded in an isolated ClassLoader
- Frontend extensions: dynamically loaded IIFE modules sharing host React / Recharts, able to register any `_render` type
- Builtin packs: `builtin_db`, `builtin_http`, `builtin_chart`, `builtin_export`

### Business Integration

- Datasource management: PostgreSQL / MySQL / Oracle, connection test, schema introspection and cache, runtime loading of external JDBC drivers
- API management: URL / method / parameter template / encrypted auth config / connectivity test
- Tool call auditing: `ba_tool_invocations` records tool, user, conversation, duration, and status

### Security

- JWT authentication with `AccessGuard` role/ownership checks
- Credentials encrypted with AES at rest; master key persisted or pinned by environment variable
- `OutboundPolicy` SSRF protection: blocks loopback, private networks, and cloud metadata, with an allow-list
- Tool whitelist injects only low-risk tools by default; anonymous sessions cannot call restricted tools
- Configurable CORS, outbound URL parameter encoding, raw payloads not logged by default

### Ownership and Publishing

- Unified ownership model for skills and extensions: `owner_id` + `team_id` + `visibility` (personal/team/public) + `publish_status` (draft/pending/approved/rejected)
- Declarative extension packs can be imported by any user (jar packs remain admin-only); imports are owned by the user or the current team
- New publish review flow: owner requests publish → admin approves or rejects (with a reason) → owner can withdraw/unpublish
- Visibility is enforced for lists, detail, tool catalog, `{{extension:code}}` resolution, and agent binding
- New "Publish review" admin menu and status badges (not published / pending / published / rejected)

### Chat Experience

- Tool cards now show concrete details: tool display name (from the extension manifest), tool kind (SQL query / API call / render output / export / code plugin), owning extension pack, triggering skill pack, and tool key — replacing generic labels such as "querying the business system"
- The same metadata is persisted in the thinking chain (`ba_thinking_chain_entries` gained `tool_display_name` / `extension_code` / `extension_name` / `tool_kind` / `skill_name`), so historical conversations render identically

### Security Hardening

- New startup guard `StartupSecurityGuard`: the `prod` profile refuses to boot while the repository-default JWT secret is in use or a fresh AES master key is generated; other environments log a prominent warning
- New dependency-free pre-commit secret scan `.githooks/pre-commit` and installer `scripts/install-git-hooks.sh`
- SECURITY.md documents repository security settings and the incident response order for leaked credentials

### Documentation

- All documentation is now English-first (`README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CHANGELOG.md`, `docs/*`), with the original Chinese kept as `*.zh-CN.md` and a language switcher at the top of every page

### Engineering

- Maven multi-module: `extension-api` (SPI) + `backend`
- Backend unit tests (engine parameter validation, read-only SQL validation, outbound policy, permission checks)
- `Dockerfile` + `docker-compose.yml`; the `postgres` container creates the schema on first start
- GitHub Actions CI (backend tests + frontend build)
- `/actuator/health` health endpoint
