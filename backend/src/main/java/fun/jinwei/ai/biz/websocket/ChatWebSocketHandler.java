package fun.jinwei.ai.biz.websocket;

import fun.jinwei.ai.biz.dto.request.SendMessageRequest;
import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.entity.Message;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.service.MessageService;
import fun.jinwei.ai.biz.service.agent.AgentLoop;
import fun.jinwei.ai.biz.service.llm.StreamingChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 聊天 WebSocket 控制器 - 处理 STOMP 消息
 *
 * 客户端发送: /app/chat/{conversationId}
 * 服务端推送: /user/queue/messages/{conversationId}
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketHandler {

    private final AgentLoop agentLoop;
    private final MessageService messageService;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理用户聊天消息
     */
    @MessageMapping("/chat/{conversationId}")
    public void handleMessage(@DestinationVariable Long conversationId,
                              @Payload SendMessageRequest request,
                              Principal principal) {
        try {
            // 校验 Principal
            if (principal == null) {
                log.error("WebSocket 消息缺少 Principal，对话ID={}", conversationId);
                return;
            }

            String username = principal.getName();
            String destination = "/queue/messages/" + conversationId;

            // 从 Principal 中提取用户 ID
            Long userId = extractUserId(principal);
            log.info("收到聊天消息: 对话ID={}, 用户ID={}, 内容={}",
                    conversationId, userId, request.getContent());

            // 验证对话归属
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElse(null);

            if (conversation == null) {
                sendError(username, destination, "对话不存在");
                return;
            }

            if (!conversation.getUser().getId().equals(userId)) {
                sendError(username, destination, "无权访问此对话");
                return;
            }

            // 保存用户消息到数据库
            Message userMessage = messageService.saveUserMessage(conversation, request.getContent());

            // 推送用户消息已保存事件 (让前端确认)
            messagingTemplate.convertAndSendToUser(
                    username, destination,
                    MessageSavedEvent.of(userMessage.getId(), userMessage.getSequenceNum()));

            // 执行 Agent 推理循环，流式推送回复
            agentLoop.execute(conversation, userMessage, username)
                    .subscribe(
                            chunk -> {
                                // 流式推送每个 chunk
                                messagingTemplate.convertAndSendToUser(
                                        username, destination, chunk);
                            },
                            error -> {
                                log.error("Agent 推理出错: 对话ID={}", conversationId, error);
                                sendError(username, destination,
                                        "处理消息时出错: " + error.getMessage());
                            }
                    );

        } catch (Exception e) {
            log.error("WebSocket 消息处理异常: 对话ID={}", conversationId, e);
            try {
                messagingTemplate.convertAndSendToUser(
                        principal != null ? principal.getName() : "unknown",
                        "/queue/messages/" + conversationId,
                        StreamingChunk.error("服务器内部错误: " + e.getMessage()));
            } catch (Exception ex) {
                log.error("发送错误消息也失败了", ex);
            }
        }
    }

    /** 发送错误消息到客户端 */
    private void sendError(String username, String destination, String errorMessage) {
        try {
            messagingTemplate.convertAndSendToUser(
                    username, destination,
                    StreamingChunk.error(errorMessage));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    /** 从 Principal 中提取用户 ID */
    private Long extractUserId(Principal principal) {
        if (principal instanceof WebSocketAuthInterceptor.UserPrincipal userPrincipal) {
            return userPrincipal.userId();
        }
        throw new IllegalStateException("未识别的 Principal 类型: " + principal.getClass().getName());
    }

    /**
     * 消息已保存事件 (内部推送)
     */
    public record MessageSavedEvent(String type, Object payload) {
        public static MessageSavedEvent of(Long messageId, Integer sequenceNum) {
            return new MessageSavedEvent("message_saved",
                    java.util.Map.of("messageId", messageId, "sequenceNum", sequenceNum));
        }
    }
}
