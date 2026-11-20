package fun.jinwei.ai.biz.extension.api;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

/**
 * 扩展包工具契约。
 *
 * <p>扩展包内的代码插件（jar）实现该接口，平台通过独立 ClassLoader 实例化并通过
 * {@code META-INF/bizagent-extension.json} 中的 {@code implementations} 声明绑定到工具 code。</p>
 *
 * <p>实现约束：不得依赖 Spring 容器、不得创建常驻线程、不得持有平台对象引用，
 * 否则卸载时会造成 ClassLoader 泄漏。</p>
 */
public interface ExtensionTool {

    /** 包内工具唯一编码，例如 generate_chart */
    String key();

    /** 工具显示名称 */
    default String displayName() {
        return key();
    }

    /** 工具描述（供 LLM 理解用途） */
    default String description() {
        return "";
    }

    /** 参数 JSON Schema（供 LLM function calling 使用） */
    JsonNode parameterSchema();

    /** 执行工具 */
    Mono<ExtensionResult> execute(JsonNode params, ExtensionContext ctx);
}
