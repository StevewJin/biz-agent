package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ba_agent_skills",
       uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "skill_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AgentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "config_overrides", length = 5000)
    private String configOverrides;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
