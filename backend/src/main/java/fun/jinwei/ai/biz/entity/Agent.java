package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ba_agents")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String logo;

    @Column(length = 2000)
    private String description;

    @Column(name = "system_prompt", length = 5000)
    private String systemPrompt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "visibility", nullable = false, length = 20)
    @Builder.Default
    private String scope = "personal";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private LlmProvider provider;

    @Column(name = "aes_key", length = 64)
    private String aesKey;

    @Column(name = "share_token", length = 36, unique = true)
    private String shareToken;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
