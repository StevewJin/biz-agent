package fun.jinwei.ai.biz.service.share;

import jakarta.persistence.EntityNotFoundException;
import fun.jinwei.ai.biz.entity.*;
import fun.jinwei.ai.biz.repository.*;
import fun.jinwei.ai.biz.service.MessageService;
import fun.jinwei.ai.biz.service.agent.AgentLoop;
import fun.jinwei.ai.biz.service.agent.AgentService;
import fun.jinwei.ai.biz.service.llm.LlmProviderManager;
import fun.jinwei.ai.biz.service.llm.StreamingChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPublicService {

    private static final int ANONYMOUS_MAX_MESSAGES = 3;
    private static final int ANONYMOUS_MAX_CONVERSATIONS = 1;

    private final AgentService agentService;
    private final AgentLoop agentLoop;
    private final MessageService messageService;
    private final ConversationRepository conversationRepository;
    private final ShareSessionRepository shareSessionRepository;
    private final AesCryptoService aesCryptoService;
    private final LlmProviderManager providerManager;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    /** 默认模型跟随 yml 配置 */
    @Value("${app.llm.deepseek.model:deepseek-flash}")
    private String defaultModel;

    public Agent getAgentInfo(String shareToken) {
        return agentService.findByShareToken(shareToken);
    }

    /** 解析外部用户名 */
    public String resolveUsername(Agent agent, String encryptedUser) {
        if (encryptedUser != null && !encryptedUser.isEmpty() && agent.getAesKey() != null) {
            return aesCryptoService.decrypt(encryptedUser, agent.getAesKey());
        }
        return null;
    }

    /** 获取公开对话列表 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConversations(String shareToken, String sessionId, String encryptedUser) {
        Agent agent = agentService.findByShareToken(shareToken);
        String resolvedUser = resolveUsername(agent, encryptedUser);
        List<ShareSession> sessions = shareSessionRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId());
        return sessions.stream()
                .filter(s -> !s.getExpired())
                .filter(s -> s.getExternalUsername() != null && matchesUser(s.getExternalUsername(), sessionId, resolvedUser))
                .map(s -> {
                    try {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("conversationId", s.getConversation().getId());
                        map.put("title", s.getConversation().getTitle());
                        map.put("messageCount", s.getMessageCount());
                        map.put("createdAt", s.getCreatedAt().toString());
                        return map;
                    } catch (EntityNotFoundException e) {
                        log.warn("ShareSession 关联的 Conversation 已不存在: sessionId={}, conversationId={}",
                                s.getId(), s.getConversation().getId());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean matchesUser(String externalUsername, String sessionId, String resolvedUser) {
        if (resolvedUser != null) {
            // 已解析用户：匹配用户名，否则回退到以 sessionId 关联的匿名会话
            return resolvedUser.equals(externalUsername)
                    || externalUsername.startsWith(sessionId + "-");
        }
        return externalUsername.startsWith(sessionId + "-");
    }

    /** 获取公开对话消息列表 */
    public List<Map<String, Object>> listMessages(Long conversationId) {
        // 复用 MessageService 以获取含思维链的 MessageResponse，避免直接返回实体暴露敏感数据
        var page = messageService.listMessages(conversationId, org.springframework.data.domain.Pageable.unpaged());
        return page.getContent().stream().map(mr -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", mr.getId());
            map.put("role", mr.getRole());
            map.put("content", mr.getContent());
            map.put("reasoningContent", mr.getReasoningContent());
            map.put("toolCallId", mr.getToolCallId());
            map.put("toolName", mr.getToolName());
            map.put("toolInput", mr.getToolInput());
            map.put("toolResult", mr.getToolResult());
            map.put("sequenceNum", mr.getSequenceNum());
            map.put("createdAt", mr.getCreatedAt() != null ? mr.getCreatedAt().toString() : null);
            map.put("thinkingChain", mr.getThinkingChain());
            return map;
        }).toList();
    }

    @Transactional
    public Conversation createSession(String shareToken, String encryptedUser, String sessionId, String ipAddress) {
        Agent agent = agentService.findByShareToken(shareToken);
        if (!agent.getEnabled()) {
            throw new IllegalStateException("智能体已下线");
        }

        // 尝试解密外部用户名
        String externalUsername = resolveUsername(agent, encryptedUser);
        boolean isAnonymous = externalUsername == null;

        // 匿名用户检查对话数量限制：查询该 sessionId 下的所有会话数
        if (isAnonymous && sessionId != null) {
            List<ShareSession> existing = shareSessionRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId());
            long convCount = existing.stream()
                    .filter(s -> !s.getExpired())
                    .filter(s -> s.getExternalUsername() != null && s.getExternalUsername().startsWith(sessionId + "-"))
                    .count();
            if (convCount >= ANONYMOUS_MAX_CONVERSATIONS) {
                throw new IllegalStateException("匿名用户仅支持 " + ANONYMOUS_MAX_CONVERSATIONS + " 个对话");
            }
        }

        // 构造 externalUsername 标识：用户名 或 sessionId-uuid
        String extUser = externalUsername != null ? externalUsername
                : (sessionId != null ? sessionId : "anon") + "-" + UUID.randomUUID().toString().substring(0, 8);

        User systemUser = agent.getOwner();
        Conversation conversation = Conversation.builder()
                .user(systemUser)
                .agent(agent)
                .title("新对话")
                .model(defaultModel)
                .build();
        conversation = conversationRepository.save(conversation);

        ShareSession session = ShareSession.builder()
                .conversation(conversation)
                .agent(agent)
                .externalUsername(extUser)
                .ipAddress(ipAddress)
                .messageCount(0)
                .conversationCount(1)
                .expired(false)
                .build();
        shareSessionRepository.save(session);

        log.info("公开访问会话创建: shareToken={}, extUser={}, isAnonymous={}", shareToken, extUser, isAnonymous);
        return conversation;
    }

    public Flux<StreamingChunk> sendMessage(Long conversationId, String content,
                                             String encryptedUser, String ipAddress) {
        ShareSession session = shareSessionRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        if (session.getExpired()) {
            return Flux.just(StreamingChunk.error("会话已过期，请重新打开链接"));
        }

        Agent agent = session.getAgent();

        // 解析访问者用户名
        String visitorUsername = null;
        if (encryptedUser != null && agent.getAesKey() != null) {
            visitorUsername = aesCryptoService.decrypt(encryptedUser, agent.getAesKey());
        }

        if (visitorUsername == null) {
            // 统计该用户所有会话的总消息数
            int totalMessages = shareSessionRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
                    .filter(s -> !s.getExpired())
                    .filter(s -> s.getExternalUsername() != null && s.getExternalUsername().equals(session.getExternalUsername()))
                    .mapToInt(ShareSession::getMessageCount)
                    .sum();
            if (totalMessages >= ANONYMOUS_MAX_MESSAGES) {
                return Flux.just(
                        StreamingChunk.error("已达免费使用上限 (" + ANONYMOUS_MAX_MESSAGES + " 条消息)，请登录后继续使用"),
                        StreamingChunk.done());
            }
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));

        // 保存用户消息
        Message userMessage = messageService.saveUserMessage(conversation, content);

        // 增加消息计数
        session.setMessageCount(session.getMessageCount() + 1);
        shareSessionRepository.save(session);

        // 执行 AgentLoop (传入访问者用户名, 匿名时为 null)
        return agentLoop.execute(conversation, userMessage, visitorUsername);
    }

    public boolean checkLimit(Long conversationId, String encryptedUser) {
        ShareSession session = shareSessionRepository.findByConversationId(conversationId).orElse(null);
        if (session == null) return false;

        Agent agent = session.getAgent();
        if (encryptedUser != null && agent.getAesKey() != null) {
            String username = aesCryptoService.decrypt(encryptedUser, agent.getAesKey());
            if (username != null) return false; // 无限制
        }
        return session.getMessageCount() >= ANONYMOUS_MAX_MESSAGES;
    }
}
