package fun.jinwei.ai.biz.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 技能包创建/更新请求。
 */
@Data
public class SkillRequest {
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private String iconColor;
    /** 支持 {{datasource:code}} 等占位符的提示词 */
    private String promptMd;
    private Map<String, Object> configSchema;
    /** {"datasources":[],"interfaces":[],"extensions":[],"tools":[]} */
    private Map<String, List<String>> resources;
    private List<String> requiredTools;
    private String keywords;
    private String triggerMode;
    private String skillVersion;
    /** 表结构注入方式：inline / lazy */
    private String schemaMode;
    private Boolean enabled;
}
