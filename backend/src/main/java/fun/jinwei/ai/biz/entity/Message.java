package fun.jinwei.ai.biz.entity;

import fun.jinwei.ai.biz.entity.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 消息实体 - 对话中的一条消息
 */
@Entity
@Table(name = "ba_messages")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属对话 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /** 消息角色: user / assistant / tool */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_role", nullable = false, length = 20)
    private MessageRole role;

    /** 消息文本内容 */
    @Column(name = "message_content", length = 5000)
    private String content;

    /** 工具调用 ID (assistant 消息中的 tool_use) */
    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    /** 工具名称 */
    @Column(name = "tool_name", length = 100)
    private String toolName;

    /** 工具输入参数 (JSON 字符串) */
    @Column(name = "tool_input", length = 5000)
    private String toolInput;

    /** 思维链推理内容 (DeepSeek V4 thinking 模式) */
    @Column(name = "reasoning_content", length = 5000)
    private String reasoningContent;

    /** 工具执行结果 */
    @Column(name = "tool_result", length = 5000)
    private String toolResult;

    /** 同对话内的顺序号 */
    @Column(name = "sequence_num", nullable = false)
    @Builder.Default
    private Integer sequenceNum = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
