package fun.jinwei.ai.biz.service.conversation;

import fun.jinwei.ai.biz.dto.response.ConversationRecordDetailResponse;
import fun.jinwei.ai.biz.dto.response.ConversationRecordResponse;
import fun.jinwei.ai.biz.dto.response.MessageResponse;
import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.entity.TeamMember;
import fun.jinwei.ai.biz.entity.enums.ConversationStatus;
import fun.jinwei.ai.biz.exception.BizException;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.repository.MessageRepository;
import fun.jinwei.ai.biz.repository.TeamMemberRepository;
import fun.jinwei.ai.biz.repository.TeamRepository;
import fun.jinwei.ai.biz.security.TeamAccess;
import fun.jinwei.ai.biz.service.MessageService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话记录（审计视角）。
 *
 * <p>权限模型与平台一致，按范围收敛：</p>
 * <pre>
 * mine  个人  → 仅本人创建的会话（所有用户可用）
 * team  团队  → 指定团队的会话（团队负责人/团队管理员，或系统管理员）
 * all   全部  → 全平台会话（仅系统管理员）
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ConversationRecordService {

    /** 详情页单次最多返回的消息条数 */
    private static final int MAX_DETAIL_MESSAGES = 500;

    public enum Scope {
        mine, team, all
    }

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccess teamAccess;

    /** 数据范围能力：可用于渲染范围选择器 */
    public record ScopeCapability(boolean all, List<Long> managedTeamIds) {
    }

    public ScopeCapability capabilities(Long userId, boolean admin) {
        if (admin) {
            return new ScopeCapability(true, List.of());
        }
        Set<Long> managed = new LinkedHashSet<>();
        teamRepository.findByOwnerId(userId).forEach(team -> managed.add(team.getId()));
        for (TeamMember member : teamMemberRepository.findByUserId(userId)) {
            String role = member.getRole();
            if ("owner".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role)) {
                managed.add(member.getTeam().getId());
            }
        }
        List<Long> ids = managed.stream().sorted(Comparator.naturalOrder()).toList();
        return new ScopeCapability(false, ids);
    }

    /** 分页查询会话记录 */
    @Transactional(readOnly = true)
    public Page<ConversationRecordResponse> list(Scope scope, Long teamId, String keyword, Long agentId,
                                                Long userId, String status, LocalDateTime from,
                                                LocalDateTime to, boolean includeDeleted,
                                                int page, int size, Long currentUserId, boolean admin) {
        Specification<Conversation> spec = buildSpec(scope, teamId, keyword, agentId, userId,
                status, from, to, includeDeleted, currentUserId, admin);

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Conversation> result = conversationRepository.findAll(spec, pageable);

        List<Long> ids = result.getContent().stream().map(Conversation::getId).toList();
        Map<Long, Long> messageCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : messageRepository.countGroupByConversationIds(ids)) {
                messageCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        return result.map(c -> toRecord(c, messageCounts.getOrDefault(c.getId(), 0L), currentUserId, admin));
    }

    /** 会话详情（含完整消息与思维链） */
    @Transactional(readOnly = true)
    public ConversationRecordDetailResponse detail(Long conversationId, Long currentUserId, boolean admin) {
        Conversation conversation = require(conversationId);
        assertVisible(conversation, currentUserId, admin);

        long messageCount = messageRepository.countByConversationId(conversationId);
        ConversationRecordResponse record = toRecord(conversation, messageCount, currentUserId, admin);

        List<MessageResponse> messages = messageService.listMessages(conversationId,
                PageRequest.of(0, MAX_DETAIL_MESSAGES)).getContent();
        return ConversationRecordDetailResponse.builder()
                .conversation(record)
                .messages(messages)
                .build();
    }

    /** 软删除会话记录 */
    @Transactional
    public void delete(Long conversationId, Long currentUserId, boolean admin) {
        Conversation conversation = require(conversationId);
        if (!canManage(conversation, currentUserId, admin)) {
            throw new AccessDeniedException("无权删除该会话");
        }
        conversation.setStatus(ConversationStatus.deleted);
        conversationRepository.save(conversation);
    }

    /** 供消息接口复用：校验当前用户能否查看该会话 */
    @Transactional(readOnly = true)
    public void assertConversationVisible(Long conversationId, Long currentUserId, boolean admin) {
        assertVisible(require(conversationId), currentUserId, admin);
    }

    private void assertVisible(Conversation conversation, Long currentUserId, boolean admin) {
        if (!canView(conversation, currentUserId, admin)) {
            throw new AccessDeniedException("无权查看该会话");
        }
    }

    private boolean canView(Conversation conversation, Long currentUserId, boolean admin) {
        if (admin || currentUserId == null) {
            return admin;
        }
        if (conversation.getUser() != null && currentUserId.equals(conversation.getUser().getId())) {
            return true;
        }
        return conversation.getTeam() != null
                && teamAccess.isTeamManager(conversation.getTeam().getId(), currentUserId);
    }

    private boolean canManage(Conversation conversation, Long currentUserId, boolean admin) {
        return canView(conversation, currentUserId, admin);
    }

    private Conversation require(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BizException(404, "会话不存在"));
    }

    private Specification<Conversation> buildSpec(Scope scope, Long teamId, String keyword, Long agentId,
                                                  Long userId, String status, LocalDateTime from,
                                                  LocalDateTime to, boolean includeDeleted,
                                                  Long currentUserId, boolean admin) {
        Scope effective = scope != null ? scope : Scope.mine;

        if (effective == Scope.all && !admin) {
            throw new AccessDeniedException("仅管理员可查看全部会话");
        }
        if (effective == Scope.team) {
            if (teamId == null) {
                throw new BizException(400, "查看团队会话需要指定团队");
            }
            if (!admin && !teamAccess.isTeamManager(teamId, currentUserId)) {
                throw new AccessDeniedException("仅团队负责人或团队管理员可查看团队会话");
            }
        }

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeDeleted) {
                predicates.add(cb.notEqual(root.get("status"), ConversationStatus.deleted));
            }
            switch (effective) {
                case mine -> predicates.add(cb.equal(root.get("user").get("id"), currentUserId));
                case team -> predicates.add(cb.equal(root.get("team").get("id"), teamId));
                default -> {
                    // all：管理员，无额外约束
                }
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (agentId != null) {
                predicates.add(cb.equal(root.get("agent").get("id"), agentId));
            }
            if (StringUtils.hasText(status)) {
                try {
                    predicates.add(cb.equal(root.get("status"),
                            ConversationStatus.valueOf(status.trim().toLowerCase())));
                } catch (IllegalArgumentException ignored) {
                    // 非法状态值忽略，避免 500
                }
            }
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(cb.lower(root.get("title")),
                        "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ConversationRecordResponse toRecord(Conversation c, long messageCount,
                                               Long currentUserId, boolean admin) {
        boolean manageable = canManage(c, currentUserId, admin);
        return ConversationRecordResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .model(c.getModel())
                .messageCount(messageCount)
                .userId(c.getUser() != null ? c.getUser().getId() : null)
                .userName(c.getUser() != null ? c.getUser().getUsername() : null)
                .teamId(c.getTeam() != null ? c.getTeam().getId() : null)
                .teamName(c.getTeam() != null ? c.getTeam().getName() : null)
                .agentId(c.getAgent() != null ? c.getAgent().getId() : null)
                .agentName(c.getAgent() != null ? c.getAgent().getName() : null)
                .manageable(manageable)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
