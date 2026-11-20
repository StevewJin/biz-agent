package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ba_thinking_chain_entries")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThinkingChainEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    /** text / thinking / tool_call / tool_result */
    @Column(name = "entry_type", nullable = false, length = 20)
    private String type;

    @Column(name = "entry_content", length = 5000)
    private String content;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "tool_input", length = 5000)
    private String toolInput;

    /** 工具展示名（来自扩展包 manifest） */
    @Column(name = "tool_display_name", length = 200)
    private String toolDisplayName;

    /** 所属扩展包编码 */
    @Column(name = "extension_code", length = 100)
    private String extensionCode;

    /** 所属扩展包名称 */
    @Column(name = "extension_name", length = 200)
    private String extensionName;

    /** 工具类型：sql / http / render / export / jar */
    @Column(name = "tool_kind", length = 20)
    private String toolKind;

    /** 触发该工具的技能包名称 */
    @Column(name = "skill_name", length = 200)
    private String skillName;

    @Column(name = "duration_ms")
    private Long durationMs;

    /** 前端渲染负载（_render 协议 JSON） */
    @Column(name = "render_json", length = 10000)
    private String renderJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
