package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 扩展包提供的前端渲染器。
 */
@Entity
@Table(name = "ba_extension_renderers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionRendererEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extension_id", nullable = false)
    private Long extensionId;

    @Column(name = "render_type", nullable = false, length = 200)
    private String renderType;

    @Column(length = 500)
    private String entry;

    @Column(name = "props_schema", length = 5000)
    private String propsSchema;

    @Column(name = "supported_tools", length = 1000)
    private String supportedTools;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
