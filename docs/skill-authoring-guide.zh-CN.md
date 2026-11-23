# 技能包编写指南

[English](skill-authoring-guide.md) · **简体中文**

## 一、技能包是什么

技能包 = **一段 Markdown 提示词** + **一组资源引用** + **可用工具白名单**。

Agent 每轮运行前，`AgentRunPlanner` 会挑出命中的技能，`SkillPromptResolver` 把提示词中的占位符展开成真实的库表结构、接口说明、扩展能力，再和工具白名单一起交给模型。

因此写技能包的本质是：**把「业务知识 + 可用资源」写清楚，让模型不需要猜。**

| 能力 | 存储字段 |
|---|---|
| 提示词主体 | `ba_skills.prompt_md` |
| 资源引用（数据源/接口/扩展包/工具） | `ba_skills.resources_json` |
| 工具白名单 | 由 `resources_json` + 占位符自动推导 |
| 触发方式 | `ba_skills.trigger_mode` / `keywords` |
| 表结构注入方式 | `ba_skills.schema_mode` |

## 二、提示词占位符

提示词中写下面四种占位符，运行时会替换为对应的资源说明（同时把相关工具加入白名单）：

| 占位符 | 展开内容 | 自动加入的工具 |
|---|---|---|
| `{{datasource:ds_code}}` | 数据源名称、只读与行数上限、表清单、字段摘要、查询方式 | 全部 `kind=sql` 工具 |
| `{{api:api_code}}` | 接口方法/URL、说明、入参 Schema、请求示例、调用方式 | 全部 `kind=http` 工具 |
| `{{extension:ext_code}}` | 扩展包名称与版本、该包全部启用工具的 key 与说明 | 该扩展包全部工具 |
| `{{tool:ext_code__tool_code}}` | 单个工具的描述与参数 Schema | 该工具 |

别名：`{{db:...}}`、`{{interface:...}}`、`{{ext:...}}` 等价可用。

注意：

- 代码必须与「数据源 / 接口 / 扩展包」中登记的 `code` 完全一致
- 资源不存在或当前用户无权访问时，占位符会被替换为一句警告，模型会被告知「请勿使用」
- 声明在 `resources_json` 中但提示词里没写占位符的资源，会在末尾自动追加一份「本技能已授权的资源」清单，确保模型知情

## 三、提示词写法建议

一份可用的技能提示词通常包含四段：

```markdown
## 角色
你是市场物料分析助手，负责解答物料入库、库存与供应商相关问题。

## 可用数据
{{datasource:ds_market}}
{{api:erp_order_query}}

## 工作流程
1. 先确认用户问的是哪类指标（入库量 / 库存 / 供应商）
2. 需要明细时调用 builtin_db__query，只查必要字段，必须带 limit
3. 需要实时单据状态时调用接口工具，不要凭记忆回答
4. 结果用表格或图表呈现，并说明数据时间范围

## 约束
- 只能查询上述数据源与接口，禁止编造表名或字段
- 涉及客户名称时做脱敏处理
- 数据为空时明确说明，不要推测
```

要点：

- **写清楚「先做什么、再做什么」**，比堆砌业务名词更有效
- **明确禁止项**（不得编造字段、不得跨库查询、必须加 limit）
- 不要重复描述表结构：占位符已经注入，重复会浪费 token
- 提示词长度建议 500~3000 字；超过 5000 字应拆成多个技能

## 四、resources_json

```json
{
  "datasources": ["ds_market"],
  "interfaces": ["erp_order_query"],
  "extensions": ["builtin_chart"],
  "tools": ["builtin_export__export_excel"]
}
```

四个数组都可省略。作用：

- `datasources` / `interfaces` / `extensions`：即使提示词里没写占位符，也会追加资源清单并注入对应工具
- `tools`：显式指定工具白名单，适合只想要某个具体工具的场景

> 只声明资源、不写占位符时，模型只能看到「有哪些资源」，看不到表结构。**推荐占位符 + resources_json 同时使用**：占位符负责注入细节，resources_json 负责兜底授权。

## 五、schema_mode：表结构注入方式

| 值 | 行为 | 适用场景 |
|---|---|---|
| `inline`（默认） | 注入每张表的主要字段（最多 40 张表、每表 12 列） | 表少、字段稳定、问法多样 |
| `lazy` | 只注入表名与表注释，字段由模型调用 `builtin_db__describe_table` 按需获取 | 表多（几百张）、字段多、token 敏感 |

`lazy` 模式会多一次工具往返，但能显著降低提示词体积。大库优先用 `lazy`。

## 六、触发方式

| trigger_mode | 行为 |
|---|---|
| `auto` | 由模型根据技能描述自行决定是否使用 |
| `keyword` | 用户消息命中 `keywords`（逗号分隔）时才注入 |
| `manual` | 只在用户显式启用该技能时注入 |

关键词匹配是「包含」判断，建议用业务专有词（如 `物料入库`、`供应商对账`），避免用 `数据`、`查询` 这类泛词。

## 七、工具白名单与风险等级

- Agent 默认只注入 `riskLevel=low` 的工具；`app.agent.allow-all-tools=true` 才会放开全部
- 技能推导出的工具会按白名单精确注入，不受上述默认限制
- 单个技能注入的工具数量受 `app.agent.max-tools` 约束
- 匿名分享场景下，含受限工具（`anonymousAllowed=false`）的技能会被自动跳过

因此：**只读查询类工具建议标 `low`；涉及写操作、外部系统变更的工具必须标 `high` 且 `anonymousAllowed=false`。**

## 八、完整示例

```json
{
  "skillCode": "market_material_inbound",
  "displayName": "物料入库分析",
  "description": "查询市场物料入库明细、库存与供应商信息",
  "category": "data",
  "triggerMode": "keyword",
  "keywords": "物料入库,库存,供应商",
  "schemaMode": "inline",
  "resources": {
    "datasources": ["ds_market"],
    "extensions": ["builtin_chart", "builtin_export"]
  },
  "promptMd": "## 角色\n你是市场物料分析助手。\n\n## 可用数据\n{{datasource:ds_market}}\n\n## 流程\n1. 明确指标与时间范围\n2. 调用 builtin_db__query，SQL 使用 #{参数} 占位符\n3. 结果超过 20 行时用 builtin_chart__bar_chart 可视化，需要下载时用 builtin_export__export_excel\n\n## 约束\n- 只查上述数据源，禁止编造表名字段\n- 必须带 limit，禁止全表扫描\n"
}
```

可直接导入的完整 SQL 版本见 [market-material-skills.sql](market-material-skills.sql)（3 个技能包示例，幂等写入）。

## 九、归属与上架

技能包与扩展包共用同一套归属与上架模型：

| 字段 | 说明 |
|---|---|
| `owner_id` | 创建人；为空表示系统内置 |
| `team_id` | 团队空间创建时写入 |
| `visibility` | `personal` / `team` / `public` |
| `publish_status` | `draft` / `pending` / `approved` / `rejected` |

- 新建技能默认 `draft`，仅自己（或本团队）可用
- 「申请上架」后进入 `pending`，管理员在「上架审核」通过后全平台可见
- 驳回时附带原因，修改提示词后可重新提交
- 系统内置技能（`owner_id` 为空）全平台可用，仅管理员可改

## 十、调试

- 管理控制台 → 技能 → 编辑：保存前可预览展开后的提示词与工具白名单，`warnings` 会提示资源不存在或无权访问
- 资源变更（同步表结构、升级扩展包、修改接口）后解析缓存会自动失效
- 工具实际调用的入参、耗时、状态都记录在 `ba_tool_invocations`，排查「模型没调用工具」时先看这张表

## 十一、常见问题

**提示词里的占位符原样出现？**
代码拼错，或使用了不支持的写法。只支持 `{{kind:code}}`，不支持 `{{ kind | code }}`。

**模型说没有可用工具？**
检查 `resources_json` 与占位符是否引用了已启用的扩展包；`{{datasource:...}}` 依赖 `builtin_db` 扩展包，`{{api:...}}` 依赖 `builtin_http`。

**提示词太长导致截断？**
改 `schema_mode=lazy`，或拆分成多个技能用 `keyword` 触发。

**个人技能别人改不了？**
技能带 `owner_id` 时仅归属人与管理员可编辑；系统内置技能 `owner_id` 为空，仅管理员可改。
