# 扩展包开发指南

[English](extension-authoring.md) · **简体中文**

扩展包（Extension Pack）是 BizAgent 的能力载体：工具能力与前端渲染能力都由扩展包提供。

## 一、目录结构

```
my-pack/
├── manifest.json          # 必需：清单
├── lib/plugin.jar         # 可选：代码类工具（jar，仅管理员可导入）
├── ui/entry.js            # 可选：前端渲染器（IIFE 构建产物）
├── ui/entry.css           # 可选：样式
└── resources/             # 可选：SQL 模板、图标等
```

打包为 zip 后，在「管理控制台 → 扩展包 → 导入扩展包」上传即可。

> 权限说明：**声明式**扩展包（sql/http/render/export）任何用户都能导入，导入后归属自己（或当前团队），
> 默认 `draft` 仅自己/团队可见；**含 jar 的代码包**等价服务端任意代码，仅管理员可导入。

## 二、manifest.json

```json
{
  "code": "sales_pack",
  "name": "销售能力包",
  "version": "1.0.0",
  "vendor": "your-team",
  "description": "销售域查询与图表",
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
      "displayName": "销售查询",
      "description": "查询销售数据，只读",
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

字段说明：

| 字段 | 说明 |
|---|---|
| `code` | 全局唯一，字母开头，仅字母数字下划线中划线 |
| `version` | 语义化版本；同 code 导入新版本会替换旧版本 |
| `packType` | `declarative`（声明式）/ `jar`（代码插件）/ `mixed` |
| `apiVersion` | 当前只支持 `1` |
| `tools[].kind` | `sql` / `http` / `render` / `export` / `jar` |
| `tools[].binding` | 声明式绑定配置，见下节 |
| `tools[].renderType` | 前端渲染类型，对应 `renderers[].type` |
| `tools[].riskLevel` | `low` / `medium` / `high`，影响默认工具注入 |
| `tools[].anonymousAllowed` | 是否允许匿名（分享页）调用 |
| `renderers[].entry` | 相对包根的前端入口 |

## 三、声明式工具（kind）

### 1. sql
```json
"binding": {
  "datasource": "ds_sales",
  "sql": "select stage, sum(amount) as amount from opportunity where region = #{region} group by stage",
  "action": "query"
}
```
- `action`：`query`（默认）/ `list_tables` / `describe_table`
- 仅允许 `SELECT` / `WITH`，单语句；命名参数用 `#{name}`，禁止字符串拼接
- `datasource` 可省略，由调用参数 `datasource` 传入（适合一个工具服务多个数据源）
- 结果行数受数据源 `queryMaxRows` 限制

### 2. http
```json
"binding": { "endpoint": "crm_order" }
```
- 只能调用「业务接口」中登记的 `apiCode`，禁止任意 URL
- 平台注入鉴权信息，扩展包与模型都拿不到明文凭证
- 出站地址受 `app.security.outbound` 策略约束（默认拦截内网/回环/云元数据）

### 3. render
```json
"binding": { "renderType": "chart_pro.radar", "llmSummary": "图表已生成" }
```
- 无副作用：校验参数后直接把参数作为前端渲染 props
- `parameterSchema.required` 会强制校验；数组/对象不会因 `asText()` 被误判为空

### 4. export
复用平台导出能力，生成 xlsx / csv 并返回下载卡片，`parameterSchema` 需包含 `filename/headers/rows`。

## 四、代码插件（jar）

仅管理员可导入。jar 中实现 `fun.jinwei.ai.biz.extension.api.ExtensionTool`：

```java
public class FunnelTool implements ExtensionTool {
    public String key() { return "funnel"; }
    public JsonNode parameterSchema() { ... }
    public Mono<ExtensionResult> execute(JsonNode params, ExtensionContext ctx) {
        List<Map<String, Object>> rows = ctx.sql("ds_sales")
                .query("select stage, count(*) c from opportunity group by stage", Map.of());
        return Mono.just(ExtensionResult.ok("查询完成", "sales_pack.funnel", Map.of("rows", rows)));
    }
}
```

jar 内 `META-INF/bizagent-extension.json`：
```json
{ "implementations": { "funnel": "com.example.FunnelTool" } }
```
也可在 manifest 的工具 `binding.class` 中声明实现类。

约束：
- 只依赖 `extension-api`，**不要**打包该模块（平台已提供）
- 不得依赖 Spring 容器、不得创建常驻线程、不得缓存平台对象（否则卸载时 ClassLoader 泄漏）
- 平台为每个包创建独立 ClassLoader，卸载时关闭

## 五、前端渲染器

入口为 IIFE 构建（rollup/vite `formats: ['iife']`），把 React / Recharts 作为 external，映射到宿主：

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

入口注册：
```ts
(window.__bizagentExt = window.__bizagentExt || {})['sales_pack'] = {
  activate() {
    return {
      renderers: [{ type: 'sales_pack.funnel', render: (props) => FunnelView(props) }]
    };
  }
};
```

平台加载流程：读取 `/api/extensions/renderers` → 注入 `/ext-assets/{code}/{version}/ui/entry.js` → 调用 `activate()` → 注册渲染器。渲染失败或类型未注册时前端降级为 JSON 展示，不会白屏。

## 六、归属与上架

导入时按当前数据上下文写入归属：

| 字段 | 说明 |
|---|---|
| `owner_id` | 导入人；为空表示系统内置包 |
| `team_id` | 团队空间导入时写入，个人空间为空 |
| `visibility` | `personal` / `team` / `public` |
| `publish_status` | `draft` / `pending` / `approved` / `rejected` |

上架流程：

1. 归属人（或团队管理者）在扩展包卡片点「申请上架」→ `pending`
2. 管理员在「上架审核」通过或驳回（驳回需填原因，可见于卡片）
3. 通过后 `public + approved`，全平台用户可见、可被技能包引用、可绑定到智能体
4. 归属人可随时「撤回申请 / 下架」，退回个人或团队可见

可见性会同时约束三处：扩展包列表与详情、`/api/extensions/tools` 工具清单、`{{extension:code}}` 占位符解析；
智能体绑定扩展包时也会校验当前用户是否可用该包。

## 七、安全与审计

- jar 包导入要求管理员；包 SHA-256 记录在 `ba_extensions.sha256`
- 每次工具调用写入 `ba_tool_invocations`（tool_key / extension_code / user_id / conversation_id / status / duration_ms）
- 声明式 SQL 只读、HTTP 仅白名单接口、导出写入受控目录
- 工具超时由 `app.extension.tool-timeout-seconds` 控制

## 八、本地调试

```bash
cd extensions-samples/chart-pro
npm install
npm run build      # 产出 ui/entry.js
npm run pack       # 产出 dist/chart_pro.bpk
```
然后在「扩展包」页面导入 `dist/chart_pro.bpk`。
