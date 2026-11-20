package fun.jinwei.ai.biz.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 技能包 / 扩展包共用的「归属 + 可见性 + 上架」判定。
 *
 * <pre>
 * ownerId 为空            → 系统内置，所有人可见
 * ownerId == 当前用户      → 本人可见
 * visibility = team       → 团队成员可见
 * visibility = public     → 需 publishStatus = approved（管理员审核通过）
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class ResourceVisibility {

    public static final String VISIBILITY_PERSONAL = "personal";
    public static final String VISIBILITY_TEAM = "team";
    public static final String VISIBILITY_PUBLIC = "public";

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    private final TeamAccess teamAccess;

    /** 是否可见（列表与详情共用） */
    public boolean isVisible(Long ownerId, Long teamId, String visibility, String publishStatus,
                             Long userId, boolean admin) {
        if (admin || ownerId == null) {
            return true;
        }
        if (userId != null && userId.equals(ownerId)) {
            return true;
        }
        if (VISIBILITY_TEAM.equalsIgnoreCase(visibility) && teamId != null) {
            return teamAccess.isMember(teamId, userId);
        }
        return VISIBILITY_PUBLIC.equalsIgnoreCase(visibility)
                && STATUS_APPROVED.equalsIgnoreCase(publishStatus);
    }

    /** 是否可管理（启停、改、删、申请上架）：系统内置仅管理员，其余看归属与团队角色 */
    public boolean canManage(Long ownerId, Long teamId, Long userId, boolean admin) {
        if (admin) {
            return true;
        }
        if (ownerId == null) {
            return false;
        }
        if (userId != null && userId.equals(ownerId)) {
            return true;
        }
        return teamId != null && teamAccess.isTeamManager(teamId, userId);
    }

    /** 是否允许提交上架（归属人或团队管理者） */
    public boolean canPublish(Long ownerId, Long teamId, Long userId, boolean admin) {
        return canManage(ownerId, teamId, userId, admin) && ownerId != null;
    }

    public static String defaultVisibility(Long teamId) {
        return teamId != null ? VISIBILITY_TEAM : VISIBILITY_PERSONAL;
    }
}
