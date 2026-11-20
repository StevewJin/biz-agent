package fun.jinwei.ai.biz.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 工具展示脱敏 — 向前端推送工具调用时隐藏敏感参数与原始结果。
 *
 * <p>工具由扩展包动态提供，因此采用「展示安全字段白名单 + 通用敏感信息遮蔽」的通用策略，
 * 不再按工具名硬编码分支。数据库中仍保存原始数据，只影响前端与思维链展示。</p>
 */
@Slf4j
@Component
public class ToolDisplaySanitizer {

    /** 允许展示给前端的参数键（低敏感度） */
    private static final Set<String> SAFE_PARAM_KEYS = Set.of(
            "chartType", "title", "filename", "format", "table", "endpoint", "datasource",
            "labels", "datasets", "headers");

    private static final Pattern JDBC_URL = Pattern.compile("jdbc:[a-zA-Z]+://[^\\s,，。；;\"')\\]]+");
    private static final Pattern ROW_COUNT = Pattern.compile("共 (\\d+) 行");
    private static final Pattern TABLE_COUNT = Pattern.compile("共 (\\d+) 张表");

    private final ObjectMapper objectMapper;

    public ToolDisplaySanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 脱敏工具输入参数 — 仅保留展示安全字段 */
    public String sanitizeInput(String toolKey, String toolInput) {
        if (toolInput == null || toolInput.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(toolInput);
            if (!root.isObject()) return "";
            ObjectNode sanitized = objectMapper.createObjectNode();
            root.fields().forEachRemaining(entry -> {
                if (SAFE_PARAM_KEYS.contains(entry.getKey())
                        && entry.getValue() != null && !entry.getValue().isNull()) {
                    sanitized.set(entry.getKey(), entry.getValue());
                }
            });
            return sanitized.isEmpty() ? "" : objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return "";
        }
    }

    /** 脱敏工具执行结果 — 只保留概要信息 */
    public String sanitizeResult(String toolKey, String result) {
        if (result == null || result.isBlank()) return "";
        String redacted = JDBC_URL.matcher(result).replaceAll("[数据库连接]");

        var rowMatcher = ROW_COUNT.matcher(redacted);
        if (rowMatcher.find()) {
            return "查询完成（" + rowMatcher.group(1) + " 行）";
        }
        var tableMatcher = TABLE_COUNT.matcher(redacted);
        if (tableMatcher.find()) {
            return "已获取表清单（" + tableMatcher.group(1) + " 张表）";
        }
        if (redacted.startsWith("Error:")) {
            return truncate(redacted, 300);
        }
        if (redacted.length() <= 300) {
            return redacted;
        }
        return "已收到结果（" + redacted.length() + " 字符）";
    }

    /** 脱敏思维链文本 — 移除数据库连接信息、文件路径等敏感内容 */
    public String sanitizeThinkingContent(String text) {
        if (text == null || text.isBlank()) return text;
        String result = JDBC_URL.matcher(text).replaceAll("[数据库连接]");
        result = result.replaceAll("(?i)^\\s*(jdbcUrl|password|driverClass)\\s*[:：][^\\n]*\\n?", "");
        result = result.replaceAll("/tmp/[^\\s,，。；;\"')\\]]+", "[文件路径]");
        result = result.replaceAll("\\n{3,}", "\n\n");
        return result;
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
