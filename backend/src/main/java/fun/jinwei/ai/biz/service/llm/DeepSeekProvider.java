package fun.jinwei.ai.biz.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;

/**
 * DeepSeek LLM 提供商实现 (兼容 OpenAI API 格式)
 * 流式调用 https://api.deepseek.com/v1/chat/completions
 */
@Slf4j
@Component
public class DeepSeekProvider implements LlmGateway {

    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final ObjectMapper objectMapper;
    private WebClient webClient;

    /** 是否输出完整请求体/SSE 原文（包含业务数据，仅调试时开启） */
    @Value("${app.llm.log-raw-payload:false}")
    private boolean logRawPayload;

    public DeepSeekProvider(
            @Value("${app.llm.deepseek.api-key}") String apiKey,
            @Value("${app.llm.deepseek.base-url}") String baseUrl,
            @Value("${app.llm.deepseek.model}") String defaultModel,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        HttpClient httpClient = HttpClient.create()
                .protocol(HttpProtocol.HTTP11)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(120));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        log.info("DeepSeekProvider 初始化完成, baseUrl={}, model={}, apiKey={}",
                baseUrl, defaultModel,
                apiKey != null && !apiKey.isBlank() ? apiKey.substring(0, 10) + "***" : "未配置");
    }

    @Override
    public String getProviderName() {
        return "DeepSeek";
    }

    @Override
    public Flux<StreamingChunk> streamChat(ChatContext context) {
        if ("your-deepseek-api-key-here".equals(apiKey) || apiKey == null || apiKey.isBlank()) {
            return mockStream(context);
        }

        String model = context.getModel() != null ? context.getModel() : defaultModel;
        Map<String, Object> requestBody = buildRequestBody(context, model);

        // Per-stream 工具调用累加器 (线程安全: 每个 Flux 订阅只有一个线程处理)
        Map<Integer, ToolCallAccumulator> toolAccum = new LinkedHashMap<>();

        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.info("DeepSeek API 调用: model={}, 消息数={}, tools={}", model,
                    context.getMessages().size(),
                    context.getTools() != null ? context.getTools().size() : 0);
            final String requestJsonFinal = requestJson;
            if (logRawPayload) {
                log.debug("DeepSeek 请求体: {}", requestJsonFinal);
            }

            return webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("DeepSeek API 返回错误: status={}, body={}",
                                                response.statusCode(),
                                                body.length() > 2000 ? body.substring(0, 2000) + "...(truncated)" : body);
                                        return new RuntimeException(
                                                "DeepSeek API 错误 " + response.statusCode() + ": " + body);
                                    }))
                    .bodyToFlux(String.class)
                    .doOnSubscribe(s -> log.info("DeepSeek HTTP 流式请求已发送, 等待响应..."))
                    .doOnCancel(() -> log.warn("DeepSeek SSE 流被取消"))
                    .doOnError(e -> log.error("DeepSeek SSE 流底层错误: {}", e.getMessage(), e))
                    .doOnNext(line -> {
                        if (logRawPayload) {
                            log.debug("DeepSeek SSE 原始行: {}", line);
                        }
                    })
                    .filter(line -> !line.isEmpty() && !line.equals("[DONE]"))
                    .map(line -> line.startsWith("data: ") ? line.substring(6) : line)
                    .flatMapIterable(line -> parseStreamLine(line, toolAccum))
                    .timeout(Duration.ofSeconds(120),
                            Flux.just(StreamingChunk.error("LLM 调用超时 (120s)"),
                                    StreamingChunk.done()))
                    .onErrorResume(e -> {
                        log.error("DeepSeek 流式调用出错: {}", e.getMessage(), e);
                        return Flux.just(StreamingChunk.error("LLM 调用出错: " + e.getMessage()));
                    });

        } catch (JsonProcessingException e) {
            log.error("构建 DeepSeek 请求体失败", e);
            return Flux.just(StreamingChunk.error("请求构建失败: " + e.getMessage()));
        }
    }

    /** 构建 OpenAI-compatible 请求体 */
    private Map<String, Object> buildRequestBody(ChatContext context, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("max_tokens", context.getMaxTokens());
        body.put("temperature", context.getTemperature());
        if (context.isThinkingEnabled()) {
            body.put("thinking", Map.of("type", "enabled"));
        }

        // 工具列表 (Phase 2)
        if (context.getTools() != null && !context.getTools().isEmpty()) {
            body.put("tools", context.getTools());
        }

        List<Map<String, Object>> messages = new ArrayList<>();

        if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", context.getSystemPrompt()));
        }

        for (ChatContext.ChatMessage msg : context.getMessages()) {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("role", msg.getRole());

            boolean hasToolCalls = msg.getToolCalls() != null && !msg.getToolCalls().isEmpty();

            // content: 有 tool_calls 时可省略字符串, 直接用 null; 否则放空字符串兜底
            if (hasToolCalls) {
                if (msg.getContent() != null && !msg.getContent().isBlank()) {
                    msgMap.put("content", msg.getContent());
                }
                // 无 content 时不放入 map, Jackson non_null 也会自动去掉 null
            } else {
                msgMap.put("content", msg.getContent() != null ? msg.getContent() : "");
            }

            if (msg.getReasoningContent() != null && !msg.getReasoningContent().isBlank()) {
                msgMap.put("reasoning_content", msg.getReasoningContent());
            }
            if (hasToolCalls) {
                List<Map<String, Object>> toolCallMaps = new ArrayList<>();
                for (ChatContext.ToolCall tc : msg.getToolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.getId());
                    tcMap.put("type", tc.getType());
                    tcMap.put("function", tc.getFunction());
                    toolCallMaps.add(tcMap);
                }
                msgMap.put("tool_calls", toolCallMaps);
            }
            if (msg.getToolCallId() != null) {
                msgMap.put("tool_call_id", msg.getToolCallId());
            }

            messages.add(msgMap);
        }

        body.put("messages", messages);
        return body;
    }

    /**
     * 解析 SSE 流中的一行数据, 返回 0-N 个 StreamingChunk
     * 工具调用增量会被累加到 acc 中, 在 finish_reason=tool_calls 时一次性产出
     */
    private List<StreamingChunk> parseStreamLine(String jsonLine, Map<Integer, ToolCallAccumulator> acc) {
        try {
            JsonNode root = objectMapper.readTree(jsonLine);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) return List.of();

            JsonNode firstChoice = choices.get(0);
            JsonNode delta = firstChoice.get("delta");

            // 处理 delta 内容 (文本/思维链/工具调用增量)
            List<StreamingChunk> results = new ArrayList<>();
            if (delta != null) {
                // 思维链
                JsonNode reasoning = delta.get("reasoning_content");
                if (reasoning != null && !reasoning.isNull() && !reasoning.asText().isEmpty()) {
                    results.add(StreamingChunk.thinkingDelta(reasoning.asText()));
                }

                // 文本内容
                JsonNode content = delta.get("content");
                if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                    results.add(StreamingChunk.textDelta(content.asText()));
                }

                // 工具调用增量 - 累加到 acc
                JsonNode toolCalls = delta.get("tool_calls");
                if (toolCalls != null && toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        int index = tc.has("index") ? tc.get("index").asInt() : 0;
                        ToolCallAccumulator tca = acc.computeIfAbsent(index, k -> new ToolCallAccumulator());
                        if (tc.has("id") && !tc.get("id").isNull()) {
                            tca.id = tc.get("id").asText();
                        }
                        if (tc.has("type") && !tc.get("type").isNull()) {
                            tca.type = tc.get("type").asText();
                        }
                        JsonNode func = tc.get("function");
                        if (func != null) {
                            if (func.has("name") && !func.get("name").isNull()) {
                                tca.name = func.get("name").asText();
                            }
                            if (func.has("arguments") && !func.get("arguments").isNull()) {
                                tca.arguments.append(func.get("arguments").asText());
                            }
                        }
                    }
                }
            }

            // finish_reason 处理
            String finishReason = firstChoice.has("finish_reason") ?
                    firstChoice.get("finish_reason").asText() : null;
            if (finishReason != null && !"null".equals(finishReason)) {
                if ("tool_calls".equals(finishReason) && !acc.isEmpty()) {
                    // 产出所有完整工具调用
                    for (ToolCallAccumulator tca : acc.values()) {
                        results.add(StreamingChunk.toolUse(tca.id, tca.name, tca.arguments.toString()));
                        log.info("工具调用已解析: id={}, name={}, args={}", tca.id, tca.name, tca.arguments);
                    }
                    acc.clear();
                }
                // stop/length 等 finish_reason 不产出额外事件, 由 AgentLoop 处理终止
            }

            return results;

        } catch (Exception e) {
            log.debug("解析 SSE 行失败: {}", jsonLine, e);
            return List.of();
        }
    }

    /** 工具调用增量累加器 */
    private static class ToolCallAccumulator {
        String id;
        String type;
        String name;
        StringBuilder arguments = new StringBuilder();
    }

    /** Mock 流式响应 - 用于开发测试 (API key 未配置时) */
    private Flux<StreamingChunk> mockStream(ChatContext context) {
        log.info("DeepSeek API key 未配置，使用 mock 响应");

        String lastUserMsg = "";
        for (int i = context.getMessages().size() - 1; i >= 0; i--) {
            if ("user".equals(context.getMessages().get(i).getRole())) {
                lastUserMsg = context.getMessages().get(i).getContent();
                break;
            }
        }

        String reply = "你说的是: \"" + lastUserMsg + "\"\n\n"
                + "当前可用的功能：\n"
                + "- 用户认证 (JWT)\n"
                + "- 对话管理 (CRUD)\n"
                + "- 消息持久化\n"
                + "- WebSocket 流式推送";

        List<StreamingChunk> chunks = new ArrayList<>();
        for (int i = 0; i < reply.length(); i++) {
            chunks.add(StreamingChunk.textDelta(String.valueOf(reply.charAt(i))));
        }

        return Flux.fromIterable(chunks)
                .delayElements(java.time.Duration.ofMillis(20),
                        reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
