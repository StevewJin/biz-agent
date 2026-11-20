package fun.jinwei.ai.biz.service.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具执行结果。
 *
 * <p>content 面向 LLM；renderType/renderProps 面向前端渲染器（_render 协议）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 是否执行成功 */
    @Builder.Default
    private boolean success = true;

    /** 结果内容 (返回给 LLM 的文本) */
    private String content;

    /** 错误信息 (success=false 时) */
    private String error;

    /** 执行耗时 (毫秒) */
    private Long durationMs;

    /** 前端渲染类型，如 chart / chart_pro.chart */
    private String renderType;

    /** 前端渲染参数 */
    private Map<String, Object> renderProps;

    public static ToolResult ok(String content) {
        return ToolResult.builder().success(true).content(content).build();
    }

    public static ToolResult ok(String content, String renderType, Map<String, Object> renderProps) {
        return ToolResult.builder()
                .success(true)
                .content(content)
                .renderType(renderType)
                .renderProps(renderProps)
                .build();
    }

    public static ToolResult fail(String error) {
        return ToolResult.builder().success(false).error(error).content("Error: " + error).build();
    }
}
