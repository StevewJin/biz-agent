package fun.jinwei.ai.biz.security;

import fun.jinwei.ai.biz.repository.TeamMemberRepository;
import fun.jinwei.ai.biz.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 团队级权限判定。
 *
 * <p>角色模型（SaaS 常见三层）：</p>
 * <pre>
 * 系统管理员 (ba_users.role_type = admin)  → 全部数据
 * 团队负责人 / 团队管理员 (owner / admin)   → 本团队数据
 * 普通成员 (member)                        → 仅本人创建的数据
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class TeamAccess {

    /** 拥有团队数据管理权的角色 */
    private static final String ROLE_OWNER = "owner";
    private static final String ROLE_TEAM_ADMIN = "admin";

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    /** 是否为团队负责人或团队管理员 */
    public boolean isTeamManager(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            return false;
        }
        if (teamRepository.existsByIdAndOwnerId(teamId, userId)) {
            return true;
        }
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .map(member -> ROLE_OWNER.equalsIgnoreCase(member.getRole())
                        || ROLE_TEAM_ADMIN.equalsIgnoreCase(member.getRole()))
                .orElse(false);
    }

    /** 是否为团队成员（含负责人与管理员） */
    public boolean isMember(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            return false;
        }
        return teamRepository.existsByIdAndOwnerId(teamId, userId)
                || teamMemberRepository.findByTeamIdAndUserId(teamId, userId).isPresent();
    }

    /**
     * 校正前端传来的团队上下文：不是该团队成员就不得使用该团队维度。
     * 防止伪造 X-Data-Context 读取其他团队的数据。
     */
    public Long resolveTeamScope(Long requestedTeamId, Long userId, boolean admin) {
        if (requestedTeamId == null) {
            return null;
        }
        if (admin || isMember(requestedTeamId, userId)) {
            return requestedTeamId;
        }
        throw new AccessDeniedException("你不是该团队成员，无法访问团队数据");
    }

    /** 团队成员或管理员 */
    public void requireMember(Long teamId, Long userId, boolean admin) {
        if (teamId == null) {
            return;
        }
        if (!admin && !isMember(teamId, userId)) {
            throw new AccessDeniedException("你不是该团队成员");
        }
    }

    /** 团队负责人/团队管理员/系统管理员 */
    public void requireTeamManager(Long teamId, Long userId, boolean admin) {
        if (admin) {
            return;
        }
        if (!isTeamManager(teamId, userId)) {
            throw new AccessDeniedException("仅团队负责人或团队管理员可执行此操作");
        }
    }

    /** 当前用户可管理（负责人/管理员身份）的团队 ID */
    public List<Long> managedTeamIds(Long userId) {
        java.util.LinkedHashSet<Long> managed = new java.util.LinkedHashSet<>();
        teamRepository.findByOwnerId(userId).forEach(team -> managed.add(team.getId()));
        for (var member : teamMemberRepository.findByUserId(userId)) {
            String role = member.getRole();
            if (ROLE_OWNER.equalsIgnoreCase(role) || ROLE_TEAM_ADMIN.equalsIgnoreCase(role)) {
                managed.add(member.getTeam().getId());
            }
        }
        return List.copyOf(managed);
    }
}
