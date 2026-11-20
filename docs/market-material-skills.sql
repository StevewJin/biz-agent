-- ============================================================
-- 技能包示例数据（导入后可在「管理控制台 → 技能」中查看与修改）
--
-- 用法：
--   psql -d bizagent -f docs/market-material-skills.sql
--
-- 说明：
--   1. 示例只引用内置扩展包（builtin_db / builtin_chart / builtin_export），
--      不依赖外部数据源，导入后即可在对话中触发。
--   2. 若要接自己的库，把 {{extension:builtin_db}} 换成 {{datasource:你的数据源code}}，
--      并把 resources_json 的 datasources 填上同一个 code。
--   3. 使用 DO 块做幂等写入，重复执行不会产生重复技能。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 物料库存查询
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bi.ba_skills WHERE skill_code = 'market_material_inventory') THEN
        INSERT INTO bi.ba_skills (
            skill_code, display_name, description, category,
            prompt_md, resources_json, keywords, trigger_mode, schema_mode,
            skill_version, pack_source, icon, icon_color, enabled
        ) VALUES (
            'market_material_inventory',
            '物料库存查询',
            '按物料编码、名称或仓库查询当前库存与可用量',
            'data',
            '## 角色
你是市场物料库存助手，负责回答物料当前库存、可用量与存放位置。

## 可用能力
{{extension:builtin_db}}

## 工作流程
1. 先确认用户要查的物料（编码 / 名称 / 仓库）
2. 用 builtin_db__query 查询，SQL 只使用 #{} 命名参数，必须带 limit
3. 结果超过 10 行时用 builtin_chart__generate_chart 生成横向柱状图
4. 回答时给出：物料编码、名称、仓库、库存量、可用量、统计时间

## 约束
- 只能执行 SELECT / WITH，禁止任何写操作
- 表名与字段必须来自 list_tables / describe_table 的返回，禁止臆造
- 查不到数据时明确说明，不要推测',
            '{"datasources":[],"interfaces":[],"extensions":["builtin_db","builtin_chart"],"tools":[]}',
            '物料,库存,可用量,仓库',
            'keyword',
            'inline',
            '1.0.0',
            'builtin',
            'package',
            '#6366f1',
            TRUE
        );
    END IF;
END $$;

-- ------------------------------------------------------------
-- 2. 物料入库趋势分析
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bi.ba_skills WHERE skill_code = 'market_material_trend') THEN
        INSERT INTO bi.ba_skills (
            skill_code, display_name, description, category,
            prompt_md, resources_json, keywords, trigger_mode, schema_mode,
            skill_version, pack_source, icon, icon_color, enabled
        ) VALUES (
            'market_material_trend',
            '物料入库趋势分析',
            '按天/周/月统计入库数量与金额，输出趋势图表与结论',
            'report',
            '## 角色
你是物料入库趋势分析助手。

## 可用能力
{{extension:builtin_db}}
{{extension:builtin_chart}}

## 工作流程
1. 明确统计维度（日 / 周 / 月）、时间范围与物料范围
2. 先 describe_table 确认时间字段与数量字段的准确名称
3. 用 builtin_db__query 按月聚合，返回两列：时间、数值
4. 调用 builtin_chart__generate_chart 生成折线或柱状图
5. 用 3 条以内的要点总结趋势（同比、环比、异常点）

## 约束
- 聚合查询必须带时间范围条件，禁止全表扫描
- 图表 labels 与 datasets 长度必须一致
- 数据缺失的月份要显式说明，不要插值',
            '{"datasources":[],"interfaces":[],"extensions":["builtin_db","builtin_chart"],"tools":[]}',
            '趋势,入库,分析,环比,同比',
            'keyword',
            'lazy',
            '1.0.0',
            'builtin',
            'trending-up',
            '#0ea5e9',
            TRUE
        );
    END IF;
END $$;

-- ------------------------------------------------------------
-- 3. 物料明细导出
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bi.ba_skills WHERE skill_code = 'market_material_export') THEN
        INSERT INTO bi.ba_skills (
            skill_code, display_name, description, category,
            prompt_md, resources_json, keywords, trigger_mode, schema_mode,
            skill_version, pack_source, icon, icon_color, enabled
        ) VALUES (
            'market_material_export',
            '物料明细导出',
            '按条件筛选物料明细并导出为 Excel / CSV 下载卡片',
            'automation',
            '## 角色
你是物料明细导出助手。

## 可用能力
{{extension:builtin_db}}
{{extension:builtin_export}}

## 工作流程
1. 确认筛选条件（仓库、物料类别、时间范围）与需要的列
2. 用 builtin_db__query 取数，单次最多 2000 行，超出时提示用户收窄条件
3. 调用 builtin_export__export_file 导出，filename 使用「物料明细_YYYYMMDD」
4. 告知用户行数、列数与文件有效期

## 约束
- 导出的列名用中文表头
- 不得导出手机号、身份证等敏感字段；确需导出时做掩码
- 空结果不要生成文件，直接说明原因',
            '{"datasources":[],"interfaces":[],"extensions":["builtin_db","builtin_export"],"tools":[]}',
            '导出,明细,Excel,CSV',
            'keyword',
            'inline',
            '1.0.0',
            'builtin',
            'file-spreadsheet',
            '#10b981',
            TRUE
        );
    END IF;
END $$;
