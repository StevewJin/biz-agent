package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 扩展包声明的工具（后端持久化副本，运行时以 manifest 为准重新同步）。
 */
@Entity
@Table(name = "ba_extension_tools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionToolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extension_id", nullable = false)
    private Long extensionId;

    @Column(name = "tool_code", nullable = false, length = 100)
    private String toolCode;

    @Column(name = "tool_key", nullable = false, length = 200)
    private String toolKey;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(length = 2000)
    private String description;

    @Column(name = "tool_kind", nullable = false, length = 20)
    private String toolKind;

    @Column(name = "parameter_schema", length = 10000)
    private String parameterSchema;

    @Column(name = "binding_json", length = 5000)
    private String bindingJson;

    @Column(name = "render_type", length = 200)
    private String renderType;

    @Column(name = "risk_level", nullable = false, length = 20)
    @Builder.Default
    private String riskLevel = "low";

    @Column(name = "anonymous_allowed", nullable = false)
    @Builder.Default
    private Boolean anonymousAllowed = Boolean.FALSE;

    @Column(name = "requires_confirm", nullable = false)
    @Builder.Default
    private Boolean requiresConfirm = Boolean.FALSE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
