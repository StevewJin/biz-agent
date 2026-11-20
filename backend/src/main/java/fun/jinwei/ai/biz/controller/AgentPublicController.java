package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.entity.Agent;
import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.service.llm.StreamingChunk;
import fun.jinwei.ai.biz.service.share.AgentPublicService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/agent/public")
@RequiredArgsConstructor
public class AgentPublicController {

    private final AgentPublicService agentPublicService;

    /** 获取智能体公开信息 + 解析用户名 */
    @GetMapping("/{shareToken}")
    public ApiResponse<Map<String, Object>> getAgentInfo(@PathVariable String shareToken,
                                                          @RequestParam(required = false) String user) {
        Agent agent = agentPublicService.getAgentInfo(shareToken);
        String username = agentPublicService.resolveUsername(agent, user);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", agent.getName());
        info.put("logo", agent.getLogo() != null ? agent.getLogo() : "");
        info.put("description", agent.getDescription() != null ? agent.getDescription() : "");
        info.put("decryptedUsername", username);
        return ApiResponse.success(info);
    }

    /** 获取公开对话列表 */
    @GetMapping("/{shareToken}/conversations")
    public ApiResponse<List<Map<String, Object>>> listConversations(@PathVariable String shareToken,
                                                                      @RequestParam String sessionId,
                                                                      @RequestParam(required = false) String user) {
        return ApiResponse.success(agentPublicService.listConversations(shareToken, sessionId, user));
    }

    /** 获取对话消息 */
    @GetMapping("/{shareToken}/conversations/{conversationId}/messages")
    public ApiResponse<List<Map<String, Object>>> getMessages(@PathVariable String shareToken,
                                                   @PathVariable Long conversationId) {
        return ApiResponse.success(agentPublicService.listMessages(conversationId));
    }

    /** 创建公开对话 */
    @PostMapping("/{shareToken}/conversations")
    public ApiResponse<Map<String, Object>> createConversation(@PathVariable String shareToken,
                                                                @RequestParam(required = false) String user,
                                                                @RequestParam(required = false, defaultValue = "anon") String sessionId,
                                                                HttpServletRequest request) {
        Conversation conv = agentPublicService.createSession(shareToken, user, sessionId, getClientIp(request));
        return ApiResponse.success(Map.of("conversationId", conv.getId(), "title", conv.getTitle()));
    }

    /** 发送消息 (SSE 流式) */
    @PostMapping(value = "/{shareToken}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable String shareToken,
                                   @RequestParam Long conversationId,
                                   @RequestParam(required = false) String user,
                                   @RequestBody Map<String, String> body,
                                   HttpServletRequest request) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(StreamingChunk.error("消息内容不能为空"));
                errorEmitter.send(StreamingChunk.done());
            } catch (IOException e) {
                // ignore
            }
            errorEmitter.complete();
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(900_000L); // 15 分钟，匹配多轮 LLM 调用

        Flux<StreamingChunk> flux = agentPublicService.sendMessage(conversationId, content, user, getClientIp(request));

        Disposable subscription = flux.subscribe(
                chunk -> {
                    try {
                        emitter.send(chunk);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("SSE 流错误: conversationId={}", conversationId, error);
                    try {
                        emitter.send(StreamingChunk.error("处理出错: " + error.getMessage()));
                    } catch (IOException ignored) {}
                    emitter.complete();
                },
                emitter::complete
        );

        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);

        return emitter;
    }

    /** 检查是否达到限制 */
    @GetMapping("/{shareToken}/check-limit")
    public ApiResponse<Map<String, Object>> checkLimit(@PathVariable String shareToken,
                                                        @RequestParam Long conversationId,
                                                        @RequestParam(required = false) String user) {
        boolean limited = agentPublicService.checkLimit(conversationId, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("limited", limited);
        return ApiResponse.success(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
