package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 工具调用审计记录。
 */
@Entity
@Table(name = "ba_tool_invocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolInvocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    /** 调用用户标识（匿名访问为空） */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "tool_key", nullable = false, length = 200)
    private String toolKey;

    @Column(name = "extension_code", length = 100)
    private String extensionCode;

    @Column(name = "params_digest", length = 500)
    private String paramsDigest;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ok";

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_msg", length = 2000)
    private String errorMsg;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
