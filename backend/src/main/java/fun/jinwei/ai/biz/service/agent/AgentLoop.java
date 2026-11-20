package fun.jinwei.ai.biz.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.entity.*;
import fun.jinwei.ai.biz.extension.ToolCatalog;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.service.MessageService;
import fun.jinwei.ai.biz.service.llm.*;
import fun.jinwei.ai.biz.service.tool.ExecutionContext;
import fun.jinwei.ai.biz.service.tool.ToolDisplaySanitizer;
import fun.jinwei.ai.biz.service.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Agent 核心循环 (Phase 3 — 思维链持久化)
 *
 * messages 表只存用户消息 + 最终 assistant 回复
 * 所有中间步骤 (中间文本 / LLM思考 / 工具调用 / 工具结果) 存入 thinking_chain_entries
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private final LlmGateway llmGateway;
    private final MessageService messageService;
    private final ConversationManager conversationManager;
    private final SystemPromptBuilder systemPromptBuilder;
    private final AgentRunPlanner agentRunPlanner;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ToolCatalog toolCatalog;
    private final LlmProviderManager providerManager;
    private final ToolDisplaySanitizer toolSanitizer;
    private final ObjectMapper objectMapper;

    @Value("${app.agent.max-turns:25}")
    private int maxTurns;

    public Flux<StreamingChunk> execute(Conversation conversation, Message userMessage, String username) {
        ChatContext context = conversationManager.buildContext(
                conversation.getId(), conversation.getModel());

        Agent agent = conversation.getAgent();
        // 智能体 → 技能包 → 资源 → 工具 展开为本次运行计划
        AgentRunPlan plan = agentRunPlanner.plan(
                conversation, agent, userMessage.getContent(), username);
        context.setSystemPrompt(plan.systemPrompt());

        // 工具白名单：技能包声明的工具 + 智能体绑定的扩展包，匿名用户再次过滤
        List<ToolCatalog.ToolRegistration> registrations =
                toolCatalog.resolve(plan.toolKeys(), plan.anonymous());
        context.setTools(toolCatalog.toOpenAITools(registrations));
        log.info("Agent 运行计划: conversationId={}, skills={}, tools={}",
                conversation.getId(), plan.skillTitles(),
                registrations.stream().map(ToolCatalog.ToolRegistration::toolKey).toList());

        // 智能体绑定的 LLM 供应商
        if (agent != null && agent.getProvider() != null) {
            String firstModel = extractFirstModel(agent.getProvider().getModels());
            context.setModel(firstModel != null ? firstModel : "default");
        }

        ExecutionContext execCtx = ExecutionContext.builder()
                .conversationId(conversation.getId())
                .userId(conversation.getUser().getId())
                .teamId(resolveTeamId(conversation, agent))
                .username(username != null ? username : "anonymous")
                .anonymous(plan.anonymous())
                .allowedDatasourceCodes(plan.allowedDatasourceCodes())
                .allowedApiCodes(plan.allowedApiCodes())
                .build();

        // 贯穿所有轮次的思维链累加器
        List<ThinkingChainEntry> thinkingChain = new ArrayList<>();

        return executeLoop(context, conversation, userMessage, execCtx, plan, thinkingChain, 1)
                .<StreamingChunk>handle((chunk, sink) -> {
                    StreamingChunk sanitized = sanitizeChunk(chunk);
                    if (sanitized != null) sink.next(sanitized);
                })
                .onErrorResume(error -> {
                    log.error("Agent 循环出错: 对话ID {}", conversation.getId(), error);
                    return Flux.just(
                            StreamingChunk.error("处理出错: " + error.getMessage()),
                            StreamingChunk.done());
                });
    }

    private Flux<StreamingChunk> executeLoop(ChatContext context, Conversation conversation,
                                              Message userMessage, ExecutionContext execCtx,
                                              AgentRunPlan plan,
                                              List<ThinkingChainEntry> thinkingChain, int turn) {
        if (turn > maxTurns) {
            log.warn("达到最大推理轮次: conversationId={}", conversation.getId());
            String stopMsg = "已达到最大推理轮次 (" + maxTurns + ")，处理已停止。已完成的分析结果见上文。";
            return Flux.just(
                    StreamingChunk.error("达到最大推理轮次 (" + maxTurns + ")，已停止"))
                    .concatWith(finishLoop(conversation, userMessage, stopMsg,
                            thinkingChain, execCtx.getUsername()));
        }

        log.info("Agent 推理第 {} 轮: conversationId={}", turn, conversation.getId());

        StringBuilder fullResponse = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        List<StreamingChunk> toolCalls = new ArrayList<>();

        return llmGateway.streamChat(context)
                .doOnNext(chunk -> {
                    switch (chunk.getType()) {
                        case "text_delta" -> {
                            if (chunk.getContent() != null) fullResponse.append(chunk.getContent());
                        }
                        case "thinking_delta" -> {
                            if (chunk.getContent() != null) reasoningContent.append(chunk.getContent());
                        }
                        case "tool_use" -> {
                            enrichToolMeta(chunk, plan);
                            toolCalls.add(chunk);
                        }
                    }
                })
                .concatWith(Flux.defer(() -> {
                    String responseText = fullResponse.toString();

                    // 将本轮 LLM 思考写入思维链
                    if (reasoningContent.length() > 0) {
                        thinkingChain.add(buildEntry("thinking", reasoningContent.toString(), null, null, null, null));
                    }

                    if (toolCalls.isEmpty()) {
                        // === 最终轮: 无工具调用 ===
                        // 将最终文本前的中间文本写入思维链
                        // (中间文本在上一个 tool_use 时已被 flush, 这里 flush 的是最终文本)
                        // 实际上最终文本之前的文本已经被上一轮的 tool_use flush 处理了,
                        // responseText 就是最终回答

                        return finishLoop(conversation, userMessage, responseText,
                                thinkingChain, execCtx.getUsername());
                    }

                    // === 中间轮: 有工具调用 ===
                    // 将中间文本写入思维链
                    if (!responseText.isBlank()) {
                        thinkingChain.add(buildEntry("text", responseText, null, null, null, null));
                    }

                    log.info("第 {} 轮检测到 {} 个工具调用: names={}",
                            turn, toolCalls.size(),
                            toolCalls.stream().map(StreamingChunk::getToolName).toList());

                    // 将工具调用请求写入思维链
                    for (StreamingChunk tc : toolCalls) {
                        thinkingChain.add(buildEntry("tool_call", null, tc.getToolCallId(),
                                tc.getToolName(), tc.getToolInput(), null, tc));
                    }

                    // 执行工具, 将结果写入思维链, 然后继续循环
                    return Flux.fromIterable(toolCalls)
                            .concatMap(tc -> executeSingleTool(tc, execCtx, plan, thinkingChain))
                            .collectList()
                            .flatMapMany(toolResults -> {
                                ChatContext nextContext = buildNextTurnContext(context, toolCalls, toolResults);

                                List<StreamingChunk> resultChunks = toolResults.stream()
                                        .map(tr -> {
                                            StreamingChunk chunk = StreamingChunk.toolResult(
                                                    tr.toolCallId(), tr.toolName(),
                                                    tr.content(), tr.durationMs(), tr.render());
                                            enrichToolMeta(chunk, plan);
                                            return chunk;
                                        })
                                        .toList();

                                return Flux.fromIterable(resultChunks)
                                        .concatWith(executeLoop(nextContext, conversation,
                                                userMessage, execCtx, plan, thinkingChain, turn + 1));
                            });
                }));
    }

    /** 最终轮: 保存 assistant 消息 + 思维链 → 生成标题 → title_updated → done */
    private Flux<StreamingChunk> finishLoop(Conversation conversation, Message userMessage,
                                             String responseText,
                                             List<ThinkingChainEntry> thinkingChain,
                                             String username) {
        return Flux.defer(() -> {
            Message saved = null;
            String finalText = responseText != null ? responseText.trim() : "";
            // 模型只调工具不说话时，补充摘要避免工具结果随历史丢失
            if (finalText.isEmpty() && !thinkingChain.isEmpty()
                    && thinkingChain.stream().anyMatch(e -> "tool_result".equals(e.getType()))) {
                finalText = "已完成工具调用，结果见下方。";
            }
            if (!finalText.isBlank()) {
                saved = messageService.saveAssistantMessage(conversation, finalText, null);
            }

            if (saved != null && !thinkingChain.isEmpty()) {
                messageService.saveThinkingChain(saved, thinkingChain);
                log.info("思维链已持久化: messageId={}, entries={}", saved.getId(), thinkingChain.size());
            }

            return tryGenerateTitle(conversation, userMessage.getContent(), responseText)
                    .map(title -> {
                        // 通过 Flux 流推送标题更新 (SSE / WebSocket 均能收到)
                        log.info("对话标题自动生成: conversationId={}, title={}", conversation.getId(), title);
                        return StreamingChunk.titleUpdated(title);
                    })
                    .concatWith(Mono.just(StreamingChunk.done()));
        });
    }

    /** 执行单个工具, 将结果追加到 thinkingChain, 返回结果记录 */
    private Mono<ToolResultRecord> executeSingleTool(StreamingChunk toolCall,
                                                      ExecutionContext execCtx,
                                                      AgentRunPlan plan,
                                                      List<ThinkingChainEntry> thinkingChain) {
        String toolName = toolCall.getToolName();
        String toolInput = toolCall.getToolInput();
        String toolCallId = toolCall.getToolCallId();

        com.fasterxml.jackson.databind.JsonNode params;
        try {
            params = toolInput != null && !toolInput.isBlank()
                    ? objectMapper.readTree(toolInput)
                    : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("解析工具参数失败: toolName={}", toolName);
            params = objectMapper.createObjectNode();
        }

        return toolCatalog.execute(toolName, params, execCtx)
                .map(result -> {
                    String renderJson = buildRenderJson(result);
                    // 工具结果写入思维链
                    thinkingChain.add(buildEntry("tool_result", result.getContent(), toolCallId,
                            toolName, null, result.getDurationMs(),
                            toolCall, renderJson));

                    return new ToolResultRecord(toolCallId, toolName,
                            result.getContent(), result.getDurationMs(), renderJson);
                });
    }

    /** 将工具渲染负载序列化为 _render 协议 JSON */
    private String buildRenderJson(ToolResult result) {
        if (result.getRenderType() == null || result.getRenderType().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", result.getRenderType());
            render.put("props", result.getRenderProps() != null ? result.getRenderProps() : Map.of());
            return objectMapper.writeValueAsString(Map.of("_render", render));
        } catch (Exception e) {
            return null;
        }
    }

    private ChatContext buildNextTurnContext(ChatContext prevContext,
                                              List<StreamingChunk> toolCalls,
                                              List<ToolResultRecord> toolResults) {
        List<ChatContext.ChatMessage> newMessages = new ArrayList<>(prevContext.getMessages());

        List<ChatContext.ToolCall> tcList = toolCalls.stream().map(tc ->
                ChatContext.ToolCall.builder()
                        .id(tc.getToolCallId())
                        .type("function")
                        .function(Map.of("name", tc.getToolName(), "arguments",
                                tc.getToolInput() != null ? tc.getToolInput() : "{}"))
                        .build()
        ).toList();

        newMessages.add(ChatContext.ChatMessage.builder()
                .role("assistant")
                .toolCalls(tcList)
                .build());

        for (ToolResultRecord tr : toolResults) {
            newMessages.add(ChatContext.ChatMessage.builder()
                    .role("tool")
                    .toolCallId(tr.toolCallId())
                    .content(tr.content())
                    .build());
        }

        return ChatContext.builder()
                .model(prevContext.getModel())
                .systemPrompt(prevContext.getSystemPrompt())
                .messages(newMessages)
                .tools(prevContext.getTools())
                .maxTokens(prevContext.getMaxTokens())
                .temperature(prevContext.getTemperature())
                .build();
    }

    // ---- helpers ----
    
    /** 解析当前数据上下文所属团队 */
    private Long resolveTeamId(Conversation conversation, Agent agent) {
        if (conversation.getTeam() != null) {
            return conversation.getTeam().getId();
        }
        if (agent != null && agent.getTeam() != null) {
            return agent.getTeam().getId();
        }
        return null;
    }
    
    private ThinkingChainEntry buildEntry(String type, String content,
                                           String toolCallId, String toolName,
                                           String toolInput, Long durationMs) {
        return buildEntry(type, content, toolCallId, toolName, toolInput, durationMs, null, null);
    }

    private ThinkingChainEntry buildEntry(String type, String content,
                                           String toolCallId, String toolName,
                                           String toolInput, Long durationMs,
                                           StreamingChunk metaSource) {
        return buildEntry(type, content, toolCallId, toolName, toolInput, durationMs, metaSource, null);
    }

    private ThinkingChainEntry buildEntry(String type, String content,
                                           String toolCallId, String toolName,
                                           String toolInput, Long durationMs,
                                           StreamingChunk metaSource, String renderJson) {
        ThinkingChainEntry.ThinkingChainEntryBuilder builder = ThinkingChainEntry.builder()
                .type(type)
                .content(content)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .toolInput(toolInput)
                .durationMs(durationMs)
                .renderJson(renderJson);
        if (metaSource != null) {
            builder.toolDisplayName(metaSource.getToolDisplayName())
                    .extensionCode(metaSource.getExtensionCode())
                    .extensionName(metaSource.getExtensionName())
                    .toolKind(metaSource.getToolKind())
                    .skillName(metaSource.getSkillName());
        }
        return builder.build();
    }

    /**
     * 为工具事件补充展示元数据：工具展示名、所属扩展包、工具类型、触发它的技能包。
     * 前端工具卡片据此显示具体工具，而不是泛化的默认文案。
     */
    private void enrichToolMeta(StreamingChunk chunk, AgentRunPlan plan) {
        if (chunk == null || chunk.getToolName() == null) {
            return;
        }
        ToolCatalog.ToolRegistration reg = toolCatalog.describe(chunk.getToolName());
        if (reg != null) {
            chunk.setToolDisplayName(reg.displayName());
            chunk.setExtensionCode(reg.extCode());
            chunk.setExtensionName(reg.extName());
            chunk.setToolKind(reg.toolKind());
        }
        if (plan != null) {
            chunk.setSkillName(plan.skillNameOf(chunk.getToolName()));
        }
    }
    
    /** 自动生成对话标题，返回生成的标题字符串 */
    private Mono<String> tryGenerateTitle(Conversation conversation, String userMessage,
                                         String assistantResponse) {
        if (!"新对话".equals(conversation.getTitle())) {
            return Mono.empty();
        }

        String prompt = "根据以下对话内容，生成一个简短的标题（15字以内），直接输出标题不要引号不要解释：\n用户：" + userMessage + "\n助手：" + assistantResponse;

        ChatContext titleCtx = ChatContext.builder()
                .model(conversation.getModel())
                .messages(List.of(
                        ChatContext.ChatMessage.builder()
                                .role("user")
                                .content(prompt)
                                .build()))
                .maxTokens(100)
                .temperature(0.3)
                .thinkingEnabled(false)
                .build();

        return llmGateway.streamChat(titleCtx)
                .filter(chunk -> "text_delta".equals(chunk.getType()) && chunk.getContent() != null)
                .map(StreamingChunk::getContent)
                .collectList()
                .map(chunks -> String.join("", chunks).trim()
                        .replace("\n", " ")
                        .replace("\"", ""))
                .filter(title -> !title.isBlank())
                .map(title -> {
                    String finalTitle = title.length() > 50 ? title.substring(0, 50) : title;
                    conversation.setTitle(finalTitle);
                    conversationRepository.save(conversation);
                    log.info("对话标题自动生成: conversationId={}, title={}", conversation.getId(), finalTitle);
                    return finalTitle;
                })
                .onErrorResume(e -> {
                    log.warn("自动生成标题失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private record ToolResultRecord(String toolCallId, String toolName, String content,
                                    Long durationMs, String render) {}

    public record TitleUpdatedEvent(String type, Long conversationId, String title) {
        public TitleUpdatedEvent(Long conversationId, String title) {
            this("title_updated", conversationId, title);
        }
    }

    /** 对推送到前端的 chunk 做工具名/参数/结果脱敏 (返回新对象，不修改原 chunk) */
    /** @return null 表示该 chunk 脱敏后无内容，应丢弃 */
    /** @return null 表示该 chunk 脱敏后无内容，应丢弃 */
    private StreamingChunk sanitizeChunk(StreamingChunk chunk) {
        // 过滤空白的 text_delta
        if ("text_delta".equals(chunk.getType())) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) return null;
            return chunk;
        }

        if ("thinking_delta".equals(chunk.getType()) && chunk.getContent() != null) {
            String sanitized = toolSanitizer.sanitizeThinkingContent(chunk.getContent());
            if (sanitized == null || isEffectivelyBlank(sanitized)) return null;
            return StreamingChunk.builder()
                    .type("thinking_delta")
                    .content(sanitized)
                    .build();
        }

        String originalName = chunk.getToolName();
        if (originalName == null) return chunk;

        if ("tool_use".equals(chunk.getType())) {
            return StreamingChunk.builder()
                    .type("tool_use")
                    .toolCallId(chunk.getToolCallId())
                    .toolName(originalName)
                    .toolInput(toolSanitizer.sanitizeInput(originalName, chunk.getToolInput()))
                    .toolDisplayName(chunk.getToolDisplayName())
                    .extensionCode(chunk.getExtensionCode())
                    .extensionName(chunk.getExtensionName())
                    .toolKind(chunk.getToolKind())
                    .skillName(chunk.getSkillName())
                    .build();
        } else if ("tool_result".equals(chunk.getType())) {
            return StreamingChunk.builder()
                    .type("tool_result")
                    .toolCallId(chunk.getToolCallId())
                    .toolName(originalName)
                    .toolResult(toolSanitizer.sanitizeResult(originalName, chunk.getToolResult()))
                    .render(chunk.getRender())
                    .durationMs(chunk.getDurationMs())
                    .toolDisplayName(chunk.getToolDisplayName())
                    .extensionCode(chunk.getExtensionCode())
                    .extensionName(chunk.getExtensionName())
                    .toolKind(chunk.getToolKind())
                    .skillName(chunk.getSkillName())
                    .build();
        }
        return chunk;
    }

    /** 内容为 null / 空白 / 仅含 * 符号 视为空 */
    private static boolean isEffectivelyBlank(String s) {
        if (s == null || s.isBlank()) return true;
        return s.replace("*", "").replaceAll("\\s+", "").isEmpty();
    }

    private String extractFirstModel(String modelsJson) {
        try {
            List<String> models = objectMapper.readValue(modelsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return models != null && !models.isEmpty() ? models.get(0) : "default";
        } catch (Exception e) {
            return "default";
        }
    }
}
