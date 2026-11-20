package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ba_share_sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ShareSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(name = "external_username", length = 200)
    private String externalUsername;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private Integer messageCount = 0;

    @Column(name = "conversation_count", nullable = false)
    @Builder.Default
    private Integer conversationCount = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean expired = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
