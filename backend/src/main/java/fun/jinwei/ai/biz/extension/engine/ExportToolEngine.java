package fun.jinwei.ai.biz.extension.engine;

import com.fasterxml.jackson.databind.JsonNode;
import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.extension.api.ExtensionResult;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.service.export.ExportService;
import fun.jinwei.ai.biz.service.tool.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 声明式导出工具引擎：生成 xlsx / csv 并返回下载卡片渲染负载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportToolEngine {

    private final ExportService exportService;

    public ExtensionResult execute(ExtensionRuntime.ToolHandle handle, JsonNode params, ExecutionContext ctx) {
        ExtensionManifest.ToolDef def = handle.tool();
        EngineSupport.assertRequired(def.getParameterSchema(), params);
        try {
            ExportService.ExportArtifact artifact = exportService.export(params, ctx.getConversationId());
            return ExtensionResult.ok(exportService.toJson(artifact),
                    "export", exportService.toRenderProps(artifact));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出失败: {}", e.getMessage(), e);
            throw new IllegalStateException("导出失败: " + e.getMessage(), e);
        }
    }
}
