package fun.jinwei.ai.biz.entity;

import fun.jinwei.ai.biz.entity.enums.ConversationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 对话实体 - 一次完整的对话会话
 */
@Entity
@Table(name = "ba_conversations")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 对话标题 */
    @Column(nullable = false, length = 200)
    @Builder.Default
    private String title = "新对话";

    /** 状态: active / archived / deleted */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.active;

    /** 使用的 LLM 提供商 ID */
    @Column(name = "provider_id")
    private Long providerId;

    /** 使用的模型名称 (如 deepseek-flash) */
    @Column(name = "model_name", nullable = false, length = 100)
    @Builder.Default
    private String model = "deepseek-flash";

    /** 关联智能体 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    /** 所属团队 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    /** 自定义系统提示词 */
    @Column(name = "system_prompt", length = 5000)
    private String systemPrompt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
