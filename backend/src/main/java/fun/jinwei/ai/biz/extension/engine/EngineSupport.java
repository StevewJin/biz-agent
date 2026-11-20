package fun.jinwei.ai.biz.extension.engine;

import com.fasterxml.jackson.databind.JsonNode;
import fun.jinwei.ai.biz.extension.api.ExtensionException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 声明式工具引擎公共工具方法。
 */
public final class EngineSupport {

    private EngineSupport() {
    }

    /** JsonNode 对象转为 Map（仅顶层字段） */
    public static Map<String, Object> toParams(JsonNode params) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (params == null || !params.isObject()) {
            return result;
        }
        params.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                result.put(entry.getKey(), null);
            } else if (value.isNumber()) {
                result.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                result.put(entry.getKey(), value.booleanValue());
            } else if (value.isArray()) {
                result.put(entry.getKey(), toList(value));
            } else if (value.isObject()) {
                result.put(entry.getKey(), toParams(value));
            } else {
                result.put(entry.getKey(), value.asText());
            }
        });
        return result;
    }

    public static List<Object> toList(JsonNode arrayNode) {
        List<Object> list = new ArrayList<>();
        arrayNode.forEach(item -> {
            if (item.isNumber()) list.add(item.numberValue());
            else if (item.isBoolean()) list.add(item.booleanValue());
            else if (item.isObject()) list.add(toParams(item));
            else if (item.isArray()) list.add(toList(item));
            else list.add(item.asText());
        });
        return list;
    }

    /** 校验 JSON Schema 中的必填项 */
    public static void assertRequired(JsonNode schema, JsonNode params) {
        if (schema == null || !schema.has("required")) {
            return;
        }
        List<String> missing = new ArrayList<>();
        schema.get("required").forEach(node -> {
            String field = node.asText();
            JsonNode value = params != null ? params.get(field) : null;
            if (value == null || value.isNull()) {
                missing.add(field);
            } else if (value.isTextual() && value.asText().isBlank()) {
                missing.add(field);
            } else if (value.isArray() && value.isEmpty()) {
                missing.add(field);
            } else if (value.isObject() && value.isEmpty()) {
                missing.add(field);
            }
        });
        if (!missing.isEmpty()) {
            throw new ExtensionException("缺少必填参数: " + String.join(", ", missing));
        }
    }

    /** 行数据格式化为 Markdown 表格文本（供 LLM 阅读） */
    public static String formatRows(List<Map<String, Object>> rows, int displayLimit) {
        if (rows == null || rows.isEmpty()) {
            return "(无数据)";
        }
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
        sb.append("|").append("---|".repeat(columns.size())).append("\n");
        int limit = Math.min(rows.size(), displayLimit);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = rows.get(i);
            List<String> cells = new ArrayList<>();
            for (String column : columns) {
                Object value = row.get(column);
                cells.add(value != null ? String.valueOf(value) : "");
            }
            sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
        }
        if (rows.size() > limit) {
            sb.append("\n(共 ").append(rows.size()).append(" 行，仅展示前 ").append(limit).append(" 行)\n");
        }
        return sb.toString();
    }

    public static List<String> columnsOf(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        return new ArrayList<>(rows.get(0).keySet());
    }
}
