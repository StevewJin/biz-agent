package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 智能体与扩展包关联。
 */
@Entity
@Table(name = "ba_agent_extensions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "extension_id", nullable = false)
    private Long extensionId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "config_overrides", length = 5000)
    private String configOverrides;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
