package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ba_skills")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_code", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "general";

    @Column(name = "config_schema", length = 5000)
    private String configSchema;

    /** 技能提示词（Markdown，支持 {{datasource:code}} 等占位符） */
    @Column(name = "prompt_md", columnDefinition = "TEXT")
    private String promptMd;

    /** 引用的资源：{"datasources":[],"interfaces":[],"extensions":[],"tools":[]} */
    @Column(name = "resources_json", length = 5000)
    private String resourcesJson;

    /** 触发关键词（逗号分隔） */
    @Column(length = 500)
    private String keywords;

    /** 触发方式：auto / keyword / manual */
    @Column(name = "trigger_mode", nullable = false, length = 20)
    @Builder.Default
    private String triggerMode = "auto";

    @Column(name = "skill_version", length = 50)
    private String skillVersion;

    /** 来源：manual / upload / builtin */
    @Column(name = "pack_source", nullable = false, length = 20)
    @Builder.Default
    private String packSource = "manual";

    @Column(name = "manifest_json", length = 5000)
    private String manifestJson;

    @Column(name = "icon_color", length = 50)
    private String iconColor;

    /** 创建人；为空表示系统内置（仅管理员可改） */
    @Column(name = "owner_id")
    private Long ownerId;

    /** 归属团队；为空表示个人 */
    @Column(name = "team_id")
    private Long teamId;

    /** 可见范围：personal / team / public */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String visibility = "personal";

    /** 上架状态：draft / pending / approved / rejected */
    @Column(name = "publish_status", nullable = false, length = 20)
    @Builder.Default
    private String publishStatus = "draft";

    /** 审核驳回原因 */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** 审核通过时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 表结构注入方式：inline（内联表字段）/ lazy（仅表名，按需查询） */
    @Column(name = "schema_mode", nullable = false, length = 20)
    @Builder.Default
    private String schemaMode = "inline";

    @Column(length = 50)
    private String icon;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
