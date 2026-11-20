package fun.jinwei.ai.biz.extension.engine;

import com.fasterxml.jackson.databind.JsonNode;
import fun.jinwei.ai.biz.entity.Datasource;
import fun.jinwei.ai.biz.entity.DatasourceColumn;
import fun.jinwei.ai.biz.entity.DatasourceTable;
import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.extension.api.ExtensionException;
import fun.jinwei.ai.biz.extension.api.ExtensionResult;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.service.datasource.DatasourceIntrospectionService;
import fun.jinwei.ai.biz.service.datasource.DatasourceService;
import fun.jinwei.ai.biz.service.datasource.ReadOnlySqlExecutor;
import fun.jinwei.ai.biz.service.tool.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 声明式 SQL 工具引擎。
 *
 * <p>支持三种动作：query（默认）/ list_tables / describe_table。
 * 数据源来源于工具 binding.datasource 或调用参数 datasource，仅允许访问当前用户可见的数据源。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlToolEngine {

    private static final int DISPLAY_ROW_LIMIT = 50;

    private final DatasourceService datasourceService;
    private final DatasourceIntrospectionService introspectionService;

    public ExtensionResult execute(ExtensionRuntime.ToolHandle handle, JsonNode params, ExecutionContext ctx) {
        ExtensionManifest.ToolDef def = handle.tool();
        String action = def.bindingString("action");
        if (action == null || action.isBlank()) {
            action = "query";
        }

        String dsCode = def.bindingString("datasource");
        if (dsCode == null || dsCode.isBlank()) {
            dsCode = textParam(params, "datasource");
        }
        if (dsCode == null || dsCode.isBlank()) {
            throw new ExtensionException("未指定数据源，请在工具绑定或调用参数中提供 datasource");
        }
        final String resolvedDsCode = dsCode;
        if (ctx.getAllowedDatasourceCodes() != null && !ctx.getAllowedDatasourceCodes().isEmpty()
                && !ctx.getAllowedDatasourceCodes().contains(resolvedDsCode)) {
            throw new ExtensionException("当前技能未授权访问数据源: " + resolvedDsCode);
        }
        Datasource ds = datasourceService.findVisibleByCode(resolvedDsCode, ctx.getUserId(), ctx.getTeamId())
                .orElseThrow(() -> new ExtensionException("数据源不存在或无权访问: " + resolvedDsCode));

        return switch (action) {
            case "list_tables" -> listTables(ds, def);
            case "describe_table" -> describeTable(ds, def, params);
            default -> query(ds, def, params);
        };
    }

    private ExtensionResult listTables(Datasource ds, ExtensionManifest.ToolDef def) {
        List<DatasourceTable> tables = introspectionService.listTables(ds.getId());
        if (tables.isEmpty()) {
            return ExtensionResult.ok("数据源 " + ds.getDsCode() + " 尚未同步表结构，请先在数据源管理中执行「同步表结构」。");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DatasourceTable table : tables) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("table", table.getTableName());
            row.put("comment", table.getTableComment() != null ? table.getTableComment() : "");
            rows.add(row);
        }
        String renderType = resolveRenderType(def, "table");
        return ExtensionResult.ok(
                "数据源 " + ds.getDsCode() + " 共 " + tables.size() + " 张表：\n"
                        + EngineSupport.formatRows(rows, 200),
                renderType, tableProps("表清单 - " + ds.getDsName(), rows));
    }

    private ExtensionResult describeTable(Datasource ds, ExtensionManifest.ToolDef def, JsonNode params) {
        String table = textParam(params, "table");
        if (table == null || table.isBlank()) {
            table = def.bindingString("table");
        }
        if (table == null || table.isBlank()) {
            throw new ExtensionException("请提供表名参数 table");
        }
        List<DatasourceColumn> columns = introspectionService.listColumns(ds.getId(), table);
        if (columns.isEmpty()) {
            return ExtensionResult.ok("未找到表 " + table + " 的字段信息，请先同步表结构或确认表名。");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DatasourceColumn column : columns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("column", column.getColumnName());
            row.put("type", column.getDataType());
            row.put("nullable", column.getNullable());
            row.put("comment", column.getColumnComment() != null ? column.getColumnComment() : "");
            rows.add(row);
        }
        String renderType = resolveRenderType(def, "table");
        return ExtensionResult.ok(
                "表 " + table + " 的字段结构：\n" + EngineSupport.formatRows(rows, 200),
                renderType, tableProps("表结构 - " + table, rows));
    }

    private ExtensionResult query(Datasource ds, ExtensionManifest.ToolDef def, JsonNode params) {
        String sql = def.bindingString("sql");
        if (sql == null || sql.isBlank()) {
            sql = textParam(params, "sql");
        }
        if (sql == null || sql.isBlank()) {
            throw new ExtensionException("缺少 SQL 语句");
        }
        Map<String, Object> paramMap = EngineSupport.toParams(params);
        // 支持 {"params": {...}} 嵌套传参，合并为扁平命名参数
        Object nested = paramMap.remove("params");
        if (nested instanceof Map<?, ?> nestedMap) {
            nestedMap.forEach((k, v) -> paramMap.put(String.valueOf(k), v));
        }
        paramMap.remove("sql");
        ReadOnlySqlExecutor executor = datasourceService.executorFor(ds);
        List<Map<String, Object>> rows = executor.query(sql, paramMap);

        String title = def.getDisplayName() != null ? def.getDisplayName() : ds.getDsName();
        String renderType = def.getRenderType();
        if (renderType == null || renderType.isBlank()) {
            renderType = "table";
        }
        Map<String, Object> props = tableProps(title, rows);
        return ExtensionResult.ok(
                "查询完成，共 " + rows.size() + " 行：\n" + EngineSupport.formatRows(rows, DISPLAY_ROW_LIMIT),
                renderType, props);
    }

    private Map<String, Object> tableProps(String title, List<Map<String, Object>> rows) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", title);
        props.put("columns", EngineSupport.columnsOf(rows));
        props.put("rows", rows);
        props.put("total", rows.size());
        return props;
    }

    private String resolveRenderType(ExtensionManifest.ToolDef def, String fallback) {
        String renderType = def.getRenderType();
        return renderType != null && !renderType.isBlank() ? renderType : fallback;
    }

    private String textParam(JsonNode params, String name) {
        if (params == null || !params.hasNonNull(name)) return null;
        String value = params.get(name).asText();
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
