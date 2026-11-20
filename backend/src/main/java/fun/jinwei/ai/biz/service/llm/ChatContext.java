package fun.jinwei.ai.biz.service.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * LLM 聊天上下文 - 封装调用 LLM 所需的所有参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatContext {

    /** 对话历史消息列表 */
    private List<ChatMessage> messages;

    /** 系统提示词 */
    private String systemPrompt;

    /** 模型名称 */
    private String model;

    /** 最大输出 token 数 */
    @Builder.Default
    private Integer maxTokens = 8192;

    /** 温度参数 (0-2) */
    @Builder.Default
    private Double temperature = 0.7;

    /** 可用工具列表 (Phase 2 启用) */
    private List<Map<String, Object>> tools;

    /** 是否启用 thinking 模式 (DeepSeek V4), 默认开启 */
    @Builder.Default
    private boolean thinkingEnabled = true;

    /**
     * 单条聊天消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        /** 角色: system / user / assistant / tool */
        private String role;

        /** 消息内容 (文本或工具调用结果) */
        private String content;

        /** 工具调用 ID (role=tool 时) */
        private String toolCallId;

        /** 工具名称 (role=tool 时, 用于从 DB 重建 tool_calls) */
        private String toolName;

        /** 思维链推理内容 (DeepSeek V4 thinking, 多轮对话需回传) */
        private String reasoningContent;

        /** 工具调用列表 (assistant 消息中的 function calling) */
        private List<ToolCall> toolCalls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private Map<String, Object> function;
    }
}
