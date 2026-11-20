package fun.jinwei.ai.biz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private String role;
    private String content;
    private String toolCallId;
    private String toolName;
    private Map<String, Object> toolInput;
    private String reasoningContent;
    private String toolResult;
    private Integer sequenceNum;
    private LocalDateTime createdAt;

    /** 思维链条目 (Phase 3: 每个 assistant 消息关联的中间步骤) */
    private List<ThinkingChainEntryResponse> thinkingChain;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThinkingChainEntryResponse {
        private Long id;
        private Integer sequenceNum;
        private String type;
        private String content;
        private String toolCallId;
        private String toolName;
        /** 工具展示名（来自扩展包 manifest） */
        private String toolDisplayName;
        /** 所属扩展包 */
        private String extensionCode;
        private String extensionName;
        /** 工具类型：sql / http / render / export / jar */
        private String toolKind;
        /** 触发该工具的技能包名称 */
        private String skillName;
        private Map<String, Object> toolInput;
        private Long durationMs;
        /** 前端渲染负载（_render 协议 JSON） */
        private String renderJson;
    }
}
