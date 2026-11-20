package fun.jinwei.ai.biz.extension.engine;

import com.fasterxml.jackson.databind.JsonNode;
import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.extension.api.ExtensionException;
import fun.jinwei.ai.biz.extension.api.ExtensionResult;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.service.tool.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 声明式渲染工具引擎。
 *
 * <p>无副作用：校验入参后直接产出前端渲染负载（_render 协议），
 * 同时给 LLM 返回简短确认，避免模型在回复中重复大量原始数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenderToolEngine {

    public ExtensionResult execute(ExtensionRuntime.ToolHandle handle, JsonNode params, ExecutionContext ctx) {
        ExtensionManifest.ToolDef def = handle.tool();
        EngineSupport.assertRequired(def.getParameterSchema(), params);

        String renderType = def.getRenderType();
        if (renderType == null || renderType.isBlank()) {
            renderType = def.bindingString("renderType");
        }
        if (renderType == null || renderType.isBlank()) {
            throw new ExtensionException("渲染工具未声明 renderType: " + def.getCode());
        }

        Map<String, Object> props = EngineSupport.toParams(params);
        String summary = def.bindingString("llmSummary");
        if (summary == null || summary.isBlank()) {
            summary = "已生成「" + (def.getDisplayName() != null ? def.getDisplayName() : def.getCode())
                    + "」，结果已在界面渲染，请直接用文字总结要点，不要重复原始数据。";
        }
        return ExtensionResult.ok(summary, renderType, props);
    }
}
