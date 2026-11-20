package fun.jinwei.ai.biz.extension.engine;

import com.fasterxml.jackson.databind.JsonNode;
import fun.jinwei.ai.biz.entity.ApiEndpoint;
import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.extension.api.ExtensionException;
import fun.jinwei.ai.biz.extension.api.ExtensionResult;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.service.apicatalog.ApiEndpointService;
import fun.jinwei.ai.biz.service.apicatalog.ApiExecutor;
import fun.jinwei.ai.biz.service.tool.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 声明式 HTTP 工具引擎。
 *
 * <p>只能调用已在「业务系统接口」中登记的接口，禁止任意 URL 请求。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpToolEngine {

    private final ApiEndpointService apiEndpointService;
    private final ApiExecutor apiExecutor;

    public ExtensionResult execute(ExtensionRuntime.ToolHandle handle, JsonNode params, ExecutionContext ctx) {
        ExtensionManifest.ToolDef def = handle.tool();
        String endpointCode = def.bindingString("endpoint");
        if (endpointCode == null || endpointCode.isBlank()) {
            endpointCode = def.bindingString("apiCode");
        }
        if (endpointCode == null || endpointCode.isBlank()) {
            endpointCode = textParam(params, "endpoint");
        }
        if (endpointCode == null || endpointCode.isBlank()) {
            throw new ExtensionException("未指定接口编码，请提供 endpoint 参数（业务系统接口管理中的接口编码）");
        }
        final String resolvedEndpointCode = endpointCode;
        if (ctx.getAllowedApiCodes() != null && !ctx.getAllowedApiCodes().isEmpty()
                && !ctx.getAllowedApiCodes().contains(resolvedEndpointCode)) {
            throw new ExtensionException("当前技能未授权调用接口: " + resolvedEndpointCode);
        }

        ApiEndpoint endpoint = apiEndpointService
                .findVisibleByCode(resolvedEndpointCode, ctx.getUserId(), ctx.getTeamId())
                .orElseThrow(() -> new ExtensionException("接口不存在或无权访问: " + resolvedEndpointCode));

        java.util.Map<String, Object> callParams = EngineSupport.toParams(params);
        callParams.remove("endpoint");
        callParams.remove("apiCode");
        Object nested = callParams.remove("params");
        if (nested instanceof java.util.Map<?, ?> nestedMap) {
            nestedMap.forEach((k, v) -> callParams.put(String.valueOf(k), v));
        }
        String result = apiExecutor.invoke(endpoint, callParams);
        return ExtensionResult.ok("接口 " + endpoint.getApiName() + " 调用结果：\n" + result);
    }

    private String textParam(JsonNode params, String name) {
        if (params == null || !params.hasNonNull(name)) return null;
        String value = params.get(name).asText();
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
