# Skill Pack Authoring Guide

**English** · [简体中文](skill-authoring-guide.zh-CN.md)

## 1. What a Skill Pack Is

A skill pack = **a Markdown prompt** + **a set of resource references** + **a tool whitelist**.

Before each run, `AgentRunPlanner` selects the matching skills and `SkillPromptResolver` expands the placeholders in the prompt into real table schemas, API descriptions, and extension capabilities, which are handed to the model together with the tool whitelist.

So the essence of authoring a skill is: **state the business knowledge and available resources clearly, so the model does not have to guess.**

| Capability | Stored in |
|---|---|
| Prompt body | `ba_skills.prompt_md` |
| Resource references (datasource/API/extension/tool) | `ba_skills.resources_json` |
| Tool whitelist | derived from `resources_json` + placeholders |
| Trigger mode | `ba_skills.trigger_mode` / `keywords` |
| Schema injection mode | `ba_skills.schema_mode` |

## 2. Prompt Placeholders

Four placeholder kinds are expanded at runtime (and pull the related tools into the whitelist):

| Placeholder | Expands to | Tools added |
|---|---|---|
| `{{datasource:ds_code}}` | Datasource name, read-only flag and row cap, table list, column summary, how to query | all `kind=sql` tools |
| `{{api:api_code}}` | Method/URL, description, input schema, request example, how to call | all `kind=http` tools |
| `{{extension:ext_code}}` | Pack name and version, keys and descriptions of all enabled tools | all tools of that pack |
| `{{tool:ext_code__tool_code}}` | Description and parameter schema of one tool | that tool |

Aliases: `{{db:...}}`, `{{interface:...}}`, `{{ext:...}}` are equivalent.

Notes:

- The code must exactly match the registered `code` of the datasource / API / extension pack
- If the resource is missing or not accessible to the current user, the placeholder becomes a warning and the model is told not to use it
- Resources declared in `resources_json` but not referenced by a placeholder are appended at the end as an "authorized resources" list, so the model still knows they exist

## 3. Writing the Prompt

A practical skill prompt usually has four sections:

```markdown
## Role
You are a market-material analyst covering inbound receipts, inventory, and suppliers.

## Available Data
{{datasource:ds_market}}
{{api:erp_order_query}}

## Workflow
1. Confirm which metric the user wants (inbound volume / inventory / suppliers)
2. For details, call builtin_db__query, select only the needed columns, always add a limit
3. For live document status, call the API tool instead of answering from memory
4. Present results as a table or chart and state the data time range

## Constraints
- Query only the datasource and API above; never invent table or column names
- Mask customer names
- When there is no data, say so explicitly; do not speculate
```

Guidelines:

- **Describe what to do first, then next** — more effective than piling up business jargon
- **State prohibitions explicitly** (no invented columns, no cross-database queries, always add a limit)
- Do not repeat the schema: placeholders already inject it, and duplication wastes tokens
- Aim for 500–3000 characters; split into multiple skills beyond ~5000

## 4. resources_json

```json
{
  "datasources": ["ds_market"],
  "interfaces": ["erp_order_query"],
  "extensions": ["builtin_chart"],
  "tools": ["builtin_export__export_excel"]
}
```

All four arrays are optional:

- `datasources` / `interfaces` / `extensions`: even without placeholders, the resources are summarized and their tools injected
- `tools`: an explicit tool whitelist, useful when you only want one specific tool

> Declaring resources without placeholders means the model only sees *which* resources exist, not their schemas. **Use both**: placeholders inject detail, `resources_json` guarantees authorization.

## 5. schema_mode: How Schemas Are Injected

| Value | Behavior | When to use |
|---|---|---|
| `inline` (default) | Inject key columns of each table (max 40 tables, 12 columns each) | Few tables, stable schema, varied questions |
| `lazy` | Inject only table names and comments; the model fetches columns via `builtin_db__describe_table` on demand | Hundreds of tables, many columns, token-sensitive |

`lazy` costs one extra tool round-trip but greatly reduces prompt size. Prefer `lazy` for large databases.

## 6. Trigger Modes

| trigger_mode | Behavior |
|---|---|
| `auto` | The model decides from the skill description |
| `keyword` | Injected only when the user message contains one of `keywords` (comma separated) |
| `manual` | Injected only when the user explicitly enables the skill |

Keyword matching is a substring test. Use domain-specific terms (e.g. `material inbound`, `supplier reconciliation`) rather than generic words like `data` or `query`.

## 7. Tool Whitelist and Risk Levels

- By default the agent injects only `riskLevel=low` tools; `app.agent.allow-all-tools=true` opens everything
- Tools derived from a skill are injected exactly as whitelisted, regardless of that default
- The number of tools per skill is capped by `app.agent.max-tools`
- In anonymous share sessions, skills containing restricted tools (`anonymousAllowed=false`) are skipped automatically

Therefore: **mark read-only query tools as `low`; mark anything that writes or mutates an external system as `high` and `anonymousAllowed=false`.**

## 8. Complete Example

```json
{
  "skillCode": "market_material_inbound",
  "displayName": "Material Inbound Analysis",
  "description": "Inbound receipts, inventory, and suppliers for market materials",
  "category": "data",
  "triggerMode": "keyword",
  "keywords": "material inbound,inventory,supplier",
  "schemaMode": "inline",
  "resources": {
    "datasources": ["ds_market"],
    "extensions": ["builtin_chart", "builtin_export"]
  },
  "promptMd": "## Role\nYou are a market-material analyst.\n\n## Available Data\n{{datasource:ds_market}}\n\n## Workflow\n1. Confirm the metric and time range\n2. Call builtin_db__query and use #{param} placeholders in SQL\n3. When more than 20 rows come back, visualize with builtin_chart__generate_chart; use builtin_export__export_file when a download is requested\n\n## Constraints\n- Query only the datasource above; never invent tables or columns\n- Always add a limit; never scan full tables\n"
}
```

A ready-to-import SQL version is available in [market-material-skills.sql](market-material-skills.sql) (three skill packs, idempotent inserts).

## 9. Ownership and Publishing

Skills and extensions share one ownership and publishing model:

| Field | Description |
|---|---|
| `owner_id` | Creator; `NULL` means builtin |
| `team_id` | Set when created in a team context |
| `visibility` | `personal` / `team` / `public` |
| `publish_status` | `draft` / `pending` / `approved` / `rejected` |

- New skills start as `draft`, usable only by the owner (or their team)
- "Request publish" moves it to `pending`; once an admin approves, it is visible platform-wide
- Rejections carry a reason; edit the prompt and resubmit
- Builtin skills (`owner_id` is `NULL`) are available platform-wide and editable only by admins

## 10. Debugging

- Admin console → Skills → Edit: preview the expanded prompt and tool whitelist before saving; `warnings` reports missing or unauthorized resources
- The resolution cache is invalidated automatically when resources change (schema sync, extension upgrade, API edit)
- Actual tool invocations (parameters, duration, status) are recorded in `ba_tool_invocations`; check that table first when "the model never calls a tool"

## 11. FAQ

**The placeholder shows up literally?**
A typo or unsupported syntax. Only `{{kind:code}}` is supported; `{{ kind | code }}` is not.

**The model says there are no tools?**
Check whether `resources_json` and the placeholders reference an enabled extension pack. `{{datasource:...}}` relies on `builtin_db`, `{{api:...}}` relies on `builtin_http`.

**The prompt is too long and gets truncated?**
Switch to `schema_mode=lazy`, or split it into several skills triggered by `keyword`.

**Others cannot edit my skill?**
Skills with an `owner_id` are editable by the owner and admins only; builtin skills (`owner_id` empty) are admin-only.
