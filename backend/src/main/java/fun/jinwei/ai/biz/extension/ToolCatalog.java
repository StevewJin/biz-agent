package fun.jinwei.ai.biz.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fun.jinwei.ai.biz.config.ExtensionProperties;
import fun.jinwei.ai.biz.entity.Extension;
import fun.jinwei.ai.biz.entity.ToolInvocation;
import fun.jinwei.ai.biz.extension.api.ExtensionException;
import fun.jinwei.ai.biz.extension.api.ExtensionResult;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.extension.engine.ExportToolEngine;
import fun.jinwei.ai.biz.extension.engine.HttpToolEngine;
import fun.jinwei.ai.biz.extension.engine.RenderToolEngine;
import fun.jinwei.ai.biz.extension.engine.SqlToolEngine;
import fun.jinwei.ai.biz.repository.ToolInvocationRepository;
import fun.jinwei.ai.biz.service.apicatalog.ApiEndpointService;
import fun.jinwei.ai.biz.service.apicatalog.ApiExecutor;
import fun.jinwei.ai.biz.service.datasource.DatasourceService;
import fun.jinwei.ai.biz.service.tool.ExecutionContext;
import fun.jinwei.ai.biz.service.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具目录：维护当前启用的工具清单，并按白名单与风险等级筛选注入 Agent 的工具。
 *
 * <p>所有工具来自扩展包；按 AgentRunPlan 的白名单解析可用工具，
 * 并在执行期做二次权限校验、超时控制与审计。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCatalog {

    private final ExtensionRuntime runtime;
    private final ExtensionProperties properties;
    private final SqlToolEngine sqlToolEngine;
    private final HttpToolEngine httpToolEngine;
    private final RenderToolEngine renderToolEngine;
    private final ExportToolEngine exportToolEngine;
    private final DatasourceService datasourceService;
    private final ApiEndpointService apiEndpointService;
    private final ApiExecutor apiExecutor;
    private final ToolInvocationRepository invocationRepository;
    private final ObjectMapper objectMapper;

    /** 技能包未声明工具时，是否放开全部工具；默认只放开低风险工具 */
    @Value("${app.agent.allow-all-tools:false}")
    private boolean allowAllTools;

    /** 工具注册信息 */
    public record ToolRegistration(String toolKey, String extCode, String extName, String displayName,
                                   String description, JsonNode parameterSchema,
                                   String riskLevel, boolean anonymousAllowed, String toolKind) {
    }

    /** 按 toolKey 查询工具展示信息（用于思维链/工具卡片展示） */
    public ToolRegistration describe(String toolKey) {
        if (toolKey == null) {
            return null;
        }
        return runtime.findTool(toolKey).map(this::toRegistration).orElse(null);
    }

    /** 列出全部启用工具 */
    public List<ToolRegistration> allTools() {
        List<ToolRegistration> registrations = new ArrayList<>();
        for (ExtensionRuntime.ToolHandle handle : runtime.allTools()) {
            registrations.add(toRegistration(handle));
        }
        return registrations;
    }

    /**
     * 解析本次对话可用的工具。
     *
     * @param toolKeys 白名单（为空时按 {@code app.agent.allow-all-tools} 决定：仅低风险工具或全部工具）
     * @param anonymous 是否匿名访问
     */
    public List<ToolRegistration> resolve(List<String> toolKeys, boolean anonymous) {
        Set<String> whitelist = toolKeys != null ? new LinkedHashSet<>(toolKeys) : Set.of();
        List<ToolRegistration> result = new ArrayList<>();
        for (ExtensionRuntime.ToolHandle handle : runtime.allTools()) {
            ToolRegistration registration = toRegistration(handle);
            if (!whitelist.isEmpty()) {
                if (!whitelist.contains(registration.toolKey())) {
                    continue;
                }
            } else if (!allowAllTools && !"low".equalsIgnoreCase(registration.riskLevel())) {
                // 无技能包时默认不放开数据库/接口/导出等风险工具
                continue;
            }
            if (anonymous && !registration.anonymousAllowed()) {
                continue;
            }
            result.add(registration);
        }
        return result;
    }

    /** 转换为 OpenAI tools 格式 */
    public List<Map<String, Object>> toOpenAITools(List<ToolRegistration> registrations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolRegistration registration : registrations) {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", registration.toolKey());
            function.put("description", registration.description());
            function.put("parameters", registration.parameterSchema());
            toolDef.put("function", function);
            result.add(toolDef);
        }
        return result;
    }

    /** 执行工具（返回面向 Agent 的消息 + 渲染负载） */
    public Mono<ToolResult> execute(String toolKey, JsonNode params, ExecutionContext ctx) {
        long start = System.currentTimeMillis();
        ExtensionRuntime.ToolHandle handle = runtime.findTool(toolKey).orElse(null);
        if (handle == null) {
            return Mono.just(ToolResult.fail("未知工具: " + toolKey));
        }
        if (Boolean.FALSE.equals(handle.tool().getEnabled())) {
            return Mono.just(ToolResult.fail("工具已禁用: " + toolKey));
        }
        if (ctx.isAnonymous() && !Boolean.TRUE.equals(handle.tool().getAnonymousAllowed())) {
            return Mono.just(ToolResult.fail("匿名用户不允许执行此操作，请登录后使用"));
        }

        return Mono.fromCallable(() -> dispatch(handle, params, ctx))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(Math.max(5, properties.getToolTimeoutSeconds())))
                .map(result -> {
                    ToolResult toolResult = ToolResult.builder()
                            .success(result.isSuccess())
                            .content(result.getContent())
                            .error(result.getError())
                            .renderType(result.getRenderType())
                            .renderProps(result.getRenderProps())
                            .durationMs(System.currentTimeMillis() - start)
                            .build();
                    audit(toolKey, handle.extension(), ctx, params, toolResult);
                    return toolResult;
                })
                .onErrorResume(e -> {
                    ToolResult failed = ToolResult.fail(friendlyError(e));
                    failed.setDurationMs(System.currentTimeMillis() - start);
                    audit(toolKey, handle.extension(), ctx, params, failed);
                    return Mono.just(failed);
                });
    }

    private ExtensionResult dispatch(ExtensionRuntime.ToolHandle handle, JsonNode params, ExecutionContext ctx) {
        ExtensionManifest.ToolDef def = handle.tool();
        String kind = def.getKind() != null ? def.getKind().toLowerCase() : "jar";
        return switch (kind) {
            case "sql" -> sqlToolEngine.execute(handle, params, ctx);
            case "http" -> httpToolEngine.execute(handle, params, ctx);
            case "render" -> renderToolEngine.execute(handle, params, ctx);
            case "export" -> exportToolEngine.execute(handle, params, ctx);
            case "jar" -> executeCodeTool(handle, params, ctx);
            default -> throw new ExtensionException("不支持的工具类型: " + kind);
        };
    }

    private ExtensionResult executeCodeTool(ExtensionRuntime.ToolHandle handle, JsonNode params, ExecutionContext ctx) {
        if (handle.codeTool() == null) {
            throw new ExtensionException("扩展包代码工具未加载: " + handle.toolKey());
        }
        ExtensionContextImpl spiCtx = new ExtensionContextImpl(ctx, handle.extension().getExtCode(),
                datasourceService, apiEndpointService, apiExecutor,
                ExtensionContextImpl.resolveWorkDir(properties.getStorageDir(), handle.extension().getExtCode()));
        return handle.codeTool().execute(params, spiCtx).block();
    }

    private ToolRegistration toRegistration(ExtensionRuntime.ToolHandle handle) {
        ExtensionManifest.ToolDef def = handle.tool();
        JsonNode schema = def.getParameterSchema();
        if (schema == null || !schema.isObject()) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("type", "object");
            fallback.set("properties", objectMapper.createObjectNode());
            schema = fallback;
        }
        String description = def.getDescription() != null ? def.getDescription() : "";
        return new ToolRegistration(
                handle.toolKey(),
                handle.extension().getExtCode(),
                handle.extension().getExtName(),
                def.getDisplayName() != null ? def.getDisplayName() : def.getCode(),
                description,
                schema,
                def.getRiskLevel() != null ? def.getRiskLevel() : "low",
                Boolean.TRUE.equals(def.getAnonymousAllowed()),
                def.getKind());
    }

    private void audit(String toolKey, Extension extension, ExecutionContext ctx,
                       JsonNode params, ToolResult result) {
        try {
            invocationRepository.save(ToolInvocation.builder()
                    .conversationId(ctx != null ? ctx.getConversationId() : null)
                    .userId(ctx != null ? ctx.getUserId() : null)
                    .toolKey(toolKey)
                    .extensionCode(extension.getExtCode())
                    .paramsDigest(digest(params))
                    .status(result.isSuccess() ? "ok" : "fail")
                    .durationMs(result.getDurationMs())
                    .errorMsg(result.isSuccess() ? null : truncate(result.getError(), 1900))
                    .build());
        } catch (Exception e) {
            log.warn("工具调用审计写入失败: {}", e.getMessage());
        }
    }

    private String digest(JsonNode params) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((params != null ? params.toString() : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            return null;
        }
    }

    private String friendlyError(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return "工具执行超时（" + properties.getToolTimeoutSeconds() + " 秒），请缩小查询范围后重试";
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        log.warn("工具执行失败: {}", message);
        return message;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
