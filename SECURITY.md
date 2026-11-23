# Security Policy

**English** · [简体中文](SECURITY.zh-CN.md)

## Reporting a Vulnerability

**Please do not disclose security issues through public issues.**

Report privately via:

- Email: steve.w.jin@gmail.com
- Or GitHub → **Security → Report a vulnerability** (private advisory)

Please include:

1. Affected version / commit
2. Vulnerability type and impact (privilege escalation, SSRF, credential exposure, RCE, …)
3. Reproduction steps or a minimal PoC
4. Your severity assessment

We acknowledge reports within **5 business days** and credit reporters after a fix ships (tell us if you prefer to stay anonymous).

## Supported Versions

| Version | Security updates |
|---|---|
| `main` branch | ✅ |

## Repository and Commit Security

This repository contains **no real credentials**: database connections, LLM API keys, the JWT secret, and the AES master key are all injected through environment variables (see `.env.example`). `.env`, `data/`, and `*.key` are excluded by `.gitignore`.

Three layers of protection:

| Layer | Mechanism | Location |
|---|---|---|
| Runtime | Startup guard: with the default JWT secret, the `prod` profile refuses to boot; a freshly generated AES master key is also rejected in `prod` (dev only logs a loud warning) | `config/StartupSecurityGuard` |
| Pre-commit | Secret scan: blocks `.env` / `*.key` / `data/` and patterns such as `sk-*`, AWS/GitHub tokens, private keys, JDBC URLs with inline credentials | `.githooks/pre-commit` |
| Hosting platform | Secret scanning + push protection, one more gate at push time | GitHub repository settings |

Enable the local check after cloning:

```bash
./scripts/install-git-hooks.sh
```

Recommended GitHub settings (Settings → Code security):

- **Secret scanning**: detect committed secrets automatically
- **Push protection**: block known secret formats during push
- **Dependabot alerts**: dependency vulnerability notices

### If a credential is committed by accident

1. **Rotate first**: revoke and recreate the credential (LLM API key, database password, JWT secret) and update the deployment environment
2. Then clean history: `git filter-repo --path <file> --invert-paths`, followed by a force push
3. Ask collaborators to re-clone (existing clones still carry history)
4. Review usage: API consumption, database login logs, anomalous requests

> The order matters: cleaning history does not invalidate a leaked secret. Note that forks, mirrors, and search engine caches cannot be recalled — that is why rotation comes first.

## Deployer Responsibilities

BizAgent executes arbitrary extension packs and connects directly to business databases. **Deployers must**:

1. **Pin secrets**
   - Set `BIZAGENT_JWT_SECRET` (Base64, ≥ 64 bytes after decoding)
   - Set `BIZAGENT_MASTER_KEY` and back it up offline; losing it makes encrypted credentials unrecoverable
   - Never ship the development defaults from `application.yml`

2. **Restrict CORS and egress**
   - Put only trusted frontend origins in `BIZAGENT_CORS_ORIGINS`; never use a wildcard
   - Private and loopback addresses are blocked by default; allow specific hosts with `BIZAGENT_OUTBOUND_HOSTS`
   - In cloud environments keep the metadata address (`169.254.169.254`) unreachable

3. **Audit extension packs**
   - Jar plugins have database access and outbound request capability — equivalent to arbitrary server-side code
   - Import only trusted sources; review `manifest.json`, tool declarations, and dependencies
   - To tighten production further, disable `app.extension.allow-code-packs`

4. **Least-privilege database accounts**
   - Business datasource accounts should be read-only and scoped to the necessary schemas/tables
   - The platform-side read-only check cannot replace database-level permissions

5. **Perimeter protection**
   - Behind a reverse proxy with HTTPS, rate limiting, and access logs when exposed publicly
   - Do not expose the PostgreSQL port; only `/actuator` endpoints as needed

## Known Design Boundaries

- Declarative SQL tools allow a single `SELECT` / `WITH` statement, but **do not constrain what the query returns**; sensitive data must be protected by database permissions
- `render` tools are side-effect free, but extension frontend code runs in the host page context — tighten with CSP if required
- The audit table `ba_tool_invocations` stores parameter digests; set a retention period that matches your compliance requirements
