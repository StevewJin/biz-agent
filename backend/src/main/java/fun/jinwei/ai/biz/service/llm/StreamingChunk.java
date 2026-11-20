package fun.jinwei.ai.biz.service.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 流式响应块
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamingChunk {

    /** 事件类型: text_delta / tool_use / thinking / error / done */
    private String type;

    /** 文本增量内容 */
    private String content;

    /** 工具名称 (type=tool_use 时) */
    private String toolName;

    /** 工具输入参数 JSON (type=tool_use 时) */
    private String toolInput;

    /** 工具调用 ID */
    private String toolCallId;

    /** 工具执行结果 (type=tool_result 时) */
    private String toolResult;

    /** 前端渲染负载 JSON (type=tool_result 时，_render 协议) */
    private String render;

    /** 执行耗时毫秒 (type=tool_result 时) */
    private Long durationMs;

    /** 工具展示名（来自扩展包 manifest） */
    private String toolDisplayName;

    /** 所属扩展包 */
    private String extensionCode;
    private String extensionName;

    /** 工具类型：sql / http / render / export / jar */
    private String toolKind;

    /** 触发该工具的技能包名称 */
    private String skillName;

    /** 标题 (type=title_updated 时) */
    private String title;

    // --- 工厂方法 ---

    /** 思维链增量 (DeepSeek V4 reasoning) */
    public static StreamingChunk thinkingDelta(String text) {
        return StreamingChunk.builder()
                .type("thinking_delta")
                .content(text)
                .build();
    }

    /** 文本增量 */
    public static StreamingChunk textDelta(String text) {
        return StreamingChunk.builder()
                .type("text_delta")
                .content(text)
                .build();
    }

    /** 工具调用开始 */
    public static StreamingChunk toolUse(String toolCallId, String toolName, String toolInput) {
        return StreamingChunk.builder()
                .type("tool_use")
                .toolCallId(toolCallId)
                .toolName(toolName)
                .toolInput(toolInput)
                .build();
    }

    /** 流结束 */
    public static StreamingChunk done() {
        return StreamingChunk.builder()
                .type("done")
                .build();
    }

    /** 工具执行结果 */
    public static StreamingChunk toolResult(String toolCallId, String toolName, String result, Long durationMs) {
        return toolResult(toolCallId, toolName, result, durationMs, null);
    }

    /** 工具执行结果（含前端渲染负载） */
    public static StreamingChunk toolResult(String toolCallId, String toolName, String result,
                                            Long durationMs, String render) {
        return StreamingChunk.builder()
                .type("tool_result")
                .toolCallId(toolCallId)
                .toolName(toolName)
                .toolResult(result)
                .render(render)
                .durationMs(durationMs)
                .build();
    }

    /** 错误 */
    public static StreamingChunk error(String message) {
        return StreamingChunk.builder()
                .type("error")
                .content(message)
                .build();
    }

    /** 标题更新 */
    public static StreamingChunk titleUpdated(String title) {
        return StreamingChunk.builder()
                .type("title_updated")
                .title(title)
                .build();
    }
}
