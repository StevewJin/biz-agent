package fun.jinwei.ai.biz.service;

import fun.jinwei.ai.biz.dto.request.ConversationRequest;
import fun.jinwei.ai.biz.dto.response.ConversationResponse;
import fun.jinwei.ai.biz.entity.Agent;
import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.entity.Team;
import fun.jinwei.ai.biz.entity.User;
import fun.jinwei.ai.biz.entity.enums.ConversationStatus;
import fun.jinwei.ai.biz.exception.BizException;
import fun.jinwei.ai.biz.repository.AgentRepository;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.repository.MessageRepository;
import fun.jinwei.ai.biz.repository.TeamRepository;
import fun.jinwei.ai.biz.repository.UserRepository;
import fun.jinwei.ai.biz.security.TeamAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 对话服务 - 对话的 CRUD 操作
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AgentRepository agentRepository;
    private final TeamAccess teamAccess;

    /** 默认模型跟随 yml 配置，避免与 LLM 端点实际可用模型不一致 */
    @Value("${app.llm.deepseek.model:deepseek-flash}")
    private String defaultModel;

    /** 获取用户对话列表 (分页) */
    public Page<ConversationResponse> listUserConversations(Long userId, Pageable pageable) {
        return conversationRepository
                .findByUserIdAndStatusNotOrderByUpdatedAtDesc(
                        userId, ConversationStatus.deleted, pageable)
                .map(this::toResponse);
    }

    /** 获取单个对话详情 */
    public ConversationResponse getConversation(Long conversationId, Long userId) {
        Conversation conversation = findOwnedConversation(conversationId, userId);
        return toResponse(conversation);
    }

    /** 创建新对话 */
    @Transactional
    public ConversationResponse createConversation(Long userId, ConversationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));

        Conversation conversation = Conversation.builder()
                .user(user)
                .title(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "新对话")
                .model(StringUtils.hasText(request.getModel()) ? request.getModel() : defaultModel)
                .systemPrompt(request.getSystemPrompt())
                .team(resolveTeam(request.getTeamId(), userId))
                .agent(resolveAgent(request.getAgentId()))
                .status(ConversationStatus.active)
                .build();

        conversation = conversationRepository.save(conversation);
        return toResponse(conversation);
    }

    /** 更新对话 (标题、模型、系统提示词) */
    @Transactional
    public ConversationResponse updateConversation(Long conversationId, Long userId,
                                                    ConversationRequest request) {
        Conversation conversation = findOwnedConversation(conversationId, userId);

        if (StringUtils.hasText(request.getTitle())) {
            conversation.setTitle(request.getTitle());
        }
        if (StringUtils.hasText(request.getModel())) {
            conversation.setModel(request.getModel());
        }
        if (request.getSystemPrompt() != null) {
            conversation.setSystemPrompt(request.getSystemPrompt());
        }

        conversation = conversationRepository.save(conversation);
        return toResponse(conversation);
    }

    /** 删除对话 (软删除) */
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = findOwnedConversation(conversationId, userId);
        conversation.setStatus(ConversationStatus.deleted);
        conversationRepository.save(conversation);
    }

    /** 查找用户拥有的对话，否则抛异常 */
    private Conversation findOwnedConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BizException(404, "对话不存在"));

        if (!conversation.getUser().getId().equals(userId)) {
            throw new BizException(403, "无权访问此对话");
        }

        return conversation;
    }

    /** 会话归属团队：仅允许团队成员写入团队会话 */
    private Team resolveTeam(Long teamId, Long userId) {
        if (teamId == null) {
            return null;
        }
        if (!teamAccess.isMember(teamId, userId)) {
            throw new BizException(403, "你不是该团队成员，无法创建团队会话");
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BizException(404, "团队不存在"));
    }

    /** 关联智能体 */
    private Agent resolveAgent(Long agentId) {
        if (agentId == null) {
            return null;
        }
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new BizException(404, "智能体不存在"));
    }

    /** 实体转响应 DTO */
    private ConversationResponse toResponse(Conversation c) {
        return ConversationResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .status(c.getStatus().name())
                .model(c.getModel())
                .systemPrompt(c.getSystemPrompt())
                .messageCount(messageRepository.countByConversationId(c.getId()))
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
