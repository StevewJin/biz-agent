# Extension Pack Authoring Guide

**English** · [简体中文](extension-authoring.zh-CN.md)

Extension packs are how BizAgent gains capabilities: both tools and frontend rendering come from extension packs.

## 1. Layout

```
my-pack/
├── manifest.json          # required: manifest
├── lib/plugin.jar         # optional: code tool (jar, admin-only import)
├── ui/entry.js            # optional: frontend renderer (IIFE build output)
├── ui/entry.css           # optional: styles
└── resources/             # optional: SQL templates, icons, etc.
```

Zip the folder and upload it via **Admin console → Extensions → Import extension**.

> Permissions: **declarative** packs (sql/http/render/export) can be imported by any user and are owned by the importer (or the current team), starting as `draft`. Packs containing a **jar** are equivalent to arbitrary server-side code and remain **admin-only**.

## 2. manifest.json

```json
{
  "code": "sales_pack",
  "name": "Sales Pack",
  "version": "1.0.0",
  "vendor": "your-team",
  "description": "Sales-domain queries and charts",
  "packType": "declarative",
  "apiVersion": "1",
  "ui": { "icon": "chart-bar", "color": "#6366f1" },
  "renderers": [
    { "type": "sales_pack.funnel", "entry": "ui/entry.js", "toolKeys": ["sales_pack__funnel"] }
  ],
  "tools": [
    {
      "code": "query",
      "kind": "sql",
      "displayName": "Sales query",
      "description": "Read-only sales query",
      "binding": { "datasource": "ds_sales", "sql": "select * from orders where region = #{region}" },
      "renderType": "table",
      "riskLevel": "medium",
      "anonymousAllowed": false,
      "parameterSchema": {
        "type": "object",
        "properties": { "region": { "type": "string" } },
        "required": ["region"]
      }
    }
  ]
}
```

Fields:

| Field | Description |
|---|---|
| `code` | Globally unique; starts with a letter; letters, digits, `_`, `-` only |
| `version` | Semantic version; importing the same `code` with a new version replaces the old one |
| `packType` | `declarative` / `jar` (code plugin) / `mixed` |
| `apiVersion` | Only `1` is supported |
| `tools[].kind` | `sql` / `http` / `render` / `export` / `jar` |
| `tools[].binding` | Declarative binding, see below |
| `tools[].renderType` | Frontend render type, matching `renderers[].type` |
| `tools[].riskLevel` | `low` / `medium` / `high`; affects default tool injection |
| `tools[].anonymousAllowed` | Whether anonymous (share page) calls are allowed |
| `renderers[].entry` | Frontend entry, relative to the pack root |

## 3. Declarative Tool Kinds

### 3.1 sql
```json
"binding": {
  "datasource": "ds_sales",
  "sql": "select stage, sum(amount) as amount from opportunity where region = #{region} group by stage",
  "action": "query"
}
```
- `action`: `query` (default) / `list_tables` / `describe_table`
- Only `SELECT` / `WITH`, single statement; use `#{name}` for named parameters, never string concatenation
- `datasource` may be omitted and supplied by the call parameter `datasource` (useful when one tool serves several datasources)
- Result size is capped by the datasource `queryMaxRows`

### 3.2 http
```json
"binding": { "endpoint": "crm_order" }
```
- Only registered `apiCode` values from "APIs" can be called; arbitrary URLs are rejected
- The platform injects authentication; neither the pack nor the model ever sees plaintext credentials
- Outbound targets are constrained by `app.security.outbound` (loopback / private / cloud metadata blocked by default)

### 3.3 render
```json
"binding": { "renderType": "chart_pro.radar", "llmSummary": "Chart generated" }
```
- Side-effect free: parameters are validated and then passed straight to the frontend as render props
- `parameterSchema.required` is enforced; arrays/objects are not misjudged as empty by `asText()` semantics

### 3.4 export
Reuses the platform export capability, producing xlsx / csv download cards. `parameterSchema` must include `filename/headers/rows`.

## 4. Code Plugins (jar)

Admin-only import. Implement `fun.jinwei.ai.biz.extension.api.ExtensionTool`:

```java
public class FunnelTool implements ExtensionTool {
    public String key() { return "funnel"; }
    public JsonNode parameterSchema() { ... }
    public Mono<ExtensionResult> execute(JsonNode params, ExtensionContext ctx) {
        List<Map<String, Object>> rows = ctx.sql("ds_sales")
                .query("select stage, count(*) c from opportunity group by stage", Map.of());
        return Mono.just(ExtensionResult.ok("done", "sales_pack.funnel", Map.of("rows", rows)));
    }
}
```

Declare implementations in `META-INF/bizagent-extension.json` inside the jar:

```json
{ "implementations": { "funnel": "com.example.FunnelTool" } }
```

You may also declare the class in the manifest tool `binding.class`.

Constraints:
- Depend on `extension-api` only; **do not** bundle that module (the platform provides it)
- Do not depend on the Spring container, do not spawn long-lived threads, do not cache platform objects (otherwise the ClassLoader leaks on uninstall)
- Each pack gets its own ClassLoader, closed on uninstall

## 5. Frontend Renderers

Build the entry as IIFE (rollup/vite `formats: ['iife']`), marking React / Recharts as external and mapping them to the host:

```ts
// vite.config.ts
build: {
  lib: { entry: 'src/index.tsx', name: '__PACK__', formats: ['iife'], fileName: () => 'entry.js' },
  rollupOptions: {
    external: ['react', 'react-dom', 'react/jsx-runtime', 'recharts'],
    output: { globals: {
      react: '__EXT_HOST__.React',
      'react-dom': '__EXT_HOST__.ReactDOM',
      'react/jsx-runtime': '__EXT_HOST__.jsxRuntime',
      recharts: '__EXT_HOST__.recharts'
    }}
  }
}
```

Register the entry:

```ts
(window.__bizagentExt = window.__bizagentExt || {})['sales_pack'] = {
  activate() {
    return {
      renderers: [{ type: 'sales_pack.funnel', render: (props) => FunnelView(props) }]
    };
  }
};
```

Platform loading flow: read `/api/extensions/renderers` → inject `/ext-assets/{code}/{version}/ui/entry.js` → call `activate()` → register renderers. If rendering fails or the type is unregistered, the UI degrades to JSON output instead of a blank screen.

## 6. Ownership and Publishing

Ownership is recorded at import time from the current data context:

| Field | Description |
|---|---|
| `owner_id` | Importer; `NULL` means a builtin pack |
| `team_id` | Set when imported in a team context, `NULL` for personal |
| `visibility` | `personal` / `team` / `public` |
| `publish_status` | `draft` / `pending` / `approved` / `rejected` |

Publishing flow:

1. The owner (or a team manager) clicks "Request publish" on the pack card → `pending`
2. An admin approves or rejects it in "Publish review" (rejection requires a reason, shown on the card)
3. Once approved it becomes `public + approved`: visible platform-wide, referenceable by skills, bindable to agents
4. The owner may withdraw/unpublish at any time, falling back to personal or team visibility

Visibility applies in three places: the pack list and detail, `/api/extensions/tools`, and `{{extension:code}}` placeholder resolution. Binding a pack to an agent also verifies that the current user can use it.

## 7. Security and Auditing

- Jar import requires an administrator; the package SHA-256 is recorded in `ba_extensions.sha256`
- Every tool invocation is written to `ba_tool_invocations` (tool_key / extension_code / user_id / conversation_id / status / duration_ms)
- Declarative SQL is read-only, HTTP is limited to registered endpoints, exports go to a controlled directory
- Tool timeouts are controlled by `app.extension.tool-timeout-seconds`

## 8. Local Development

```bash
cd extensions-samples/chart-pro
npm install
npm run build      # produces ui/entry.js
npm run pack       # produces dist/chart_pro.bpk
```

Then import `dist/chart_pro.bpk` from the Extensions page.
