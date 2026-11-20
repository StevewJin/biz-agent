package fun.jinwei.ai.biz.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 统一访问控制工具：管理员判定与资源归属校验。
 *
 * <p>约定：全局资源（扩展包、技能包内置项）仅管理员可改；个人资源（智能体、数据源、接口）
 * 仅归属人可改，管理员可代为管理。</p>
 */
public final class AccessGuard {

    private AccessGuard() {
    }

    public static Long userId(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }

    public static boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** 需要管理员权限 */
    public static void requireAdmin(Authentication auth) {
        if (!isAdmin(auth)) {
            throw new AccessDeniedException("该操作需要管理员权限");
        }
    }

    /** 需要资源归属人（或管理员） */
    public static void requireOwnerOrAdmin(Long ownerId, Authentication auth) {
        if (isAdmin(auth)) {
            return;
        }
        Long userId = userId(auth);
        if (userId == null || ownerId == null || !ownerId.equals(userId)) {
            throw new AccessDeniedException("无权操作该资源");
        }
    }

    /** 是否可管理（归属人或管理员） */
    public static boolean canManage(Long ownerId, Authentication auth) {
        if (isAdmin(auth)) {
            return true;
        }
        Long userId = userId(auth);
        return userId != null && ownerId != null && ownerId.equals(userId);
    }
}
