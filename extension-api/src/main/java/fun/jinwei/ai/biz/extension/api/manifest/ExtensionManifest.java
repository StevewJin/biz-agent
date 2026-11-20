package fun.jinwei.ai.biz.extension.api.manifest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扩展包清单 (manifest.json)。
 *
 * <pre>
 * {
 *   "code": "chart_pro", "name": "高级图表", "version": "1.0.0",
 *   "packType": "mixed", "apiVersion": "1",
 *   "tools": [ {...} ],
 *   "renderers": [ {"type":"chart_pro.chart","entry":"ui/entry.js"} ],
 *   "ui": {"icon":"chart-bar"}
 * }
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtensionManifest {

    /** 扩展包编码（全局唯一，建议 snake_case） */
    private String code;

    /** 扩展包名称 */
    private String name;

    /** 版本号（语义化版本） */
    private String version;

    /** 供应商 */
    private String vendor;

    /** 描述 */
    private String description;

    /** 包类型：declarative / jar / mixed */
    @JsonProperty("packType")
    private String packType = "declarative";

    /** 平台扩展 API 版本，当前只支持 "1" */
    @JsonProperty("apiVersion")
    private String apiVersion = "1";

    /** 依赖的其它扩展包编码 */
    private List<String> dependencies = new ArrayList<>();

    /** 工具定义 */
    private List<ToolDef> tools = new ArrayList<>();

    /** 前端渲染器定义 */
    private List<RendererDef> renderers = new ArrayList<>();

    /** 前端界面配置 */
    private UiDef ui = new UiDef();

    /** 签名（预留，本期不校验） */
    private String signature;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolDef {
        /** 包内工具编码 */
        private String code;

        /** 工具类型：sql / http / render / export / jar */
        private String kind;

        @JsonProperty("displayName")
        private String displayName;

        private String description;

        /** 参数 JSON Schema */
        @JsonProperty("parameterSchema")
        private JsonNode parameterSchema;

        /** 声明式绑定配置（datasource / sql / endpoint / renderType / format ...） */
        private Map<String, Object> binding = new LinkedHashMap<>();

        /** 前端渲染类型（render 工具必须） */
        @JsonProperty("renderType")
        private String renderType;

        /** 风险级别：low / medium / high */
        @JsonProperty("riskLevel")
        private String riskLevel = "low";

        /** 是否允许匿名调用 */
        @JsonProperty("anonymousAllowed")
        private Boolean anonymousAllowed = Boolean.FALSE;

        /** 是否需要用户确认（预留） */
        @JsonProperty("requiresConfirm")
        private Boolean requiresConfirm = Boolean.FALSE;

        private Boolean enabled = Boolean.TRUE;

        public String bindingString(String key) {
            Object value = binding != null ? binding.get(key) : null;
            return value != null ? String.valueOf(value) : null;
        }

        public boolean bindingBoolean(String key, boolean defaultValue) {
            Object value = binding != null ? binding.get(key) : null;
            if (value instanceof Boolean b) return b;
            if (value == null) return defaultValue;
            return Boolean.parseBoolean(String.valueOf(value));
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RendererDef {
        /** 渲染类型，如 chart_pro.chart */
        private String type;

        /** 相对包根的入口文件，如 ui/entry.js */
        private String entry;

        /** 渲染入参 JSON Schema（可选） */
        @JsonProperty("propsSchema")
        private JsonNode propsSchema;

        /** 关联的工具编码列表（可选，仅用于展示） */
        @JsonProperty("toolKeys")
        private List<String> toolKeys = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UiDef {
        private String icon;

        private String color;

        /** 参数脱敏配置，例如 {"hideParams":["sql"]} */
        private Map<String, Object> sanitize = new LinkedHashMap<>();
    }
}
