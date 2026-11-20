package fun.jinwei.ai.biz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 技能包展示对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResponse {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private String iconColor;
    private String promptMd;
    private Map<String, Object> configSchema;
    private Map<String, Object> resources;
    private List<String> requiredTools;
    private String keywords;
    private String triggerMode;
    private String skillVersion;
    private String packSource;
    /** 表结构注入方式：inline / lazy */
    private String schemaMode;
    /** 创建人；为空表示系统内置 */
    private Long ownerId;
    private Long teamId;
    /** personal / team / public */
    private String visibility;
    /** draft / pending / approved / rejected */
    private String publishStatus;
    private String rejectReason;
    private LocalDateTime publishedAt;
    private boolean active;
    private Map<String, Object> configOverrides;
    private LocalDateTime createdAt;
}
