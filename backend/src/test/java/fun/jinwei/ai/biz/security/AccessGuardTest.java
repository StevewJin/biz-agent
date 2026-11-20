package fun.jinwei.ai.biz.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 访问控制工具回归：管理员判定与归属校验。
 */
class AccessGuardTest {

    private Authentication auth(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId, "u" + userId, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void detectsAdminByRole() {
        assertTrue(AccessGuard.isAdmin(auth(1L, "ADMIN")));
        assertFalse(AccessGuard.isAdmin(auth(2L, "USER")));
        assertFalse(AccessGuard.isAdmin(null));
    }

    @Test
    void requireAdminRejectsNormalUser() {
        assertThrows(AccessDeniedException.class, () -> AccessGuard.requireAdmin(auth(2L, "USER")));
        assertDoesNotThrow(() -> AccessGuard.requireAdmin(auth(1L, "ADMIN")));
    }

    @Test
    void ownerOrAdminOnly() {
        assertDoesNotThrow(() -> AccessGuard.requireOwnerOrAdmin(5L, auth(5L, "USER")));
        assertThrows(AccessDeniedException.class,
                () -> AccessGuard.requireOwnerOrAdmin(5L, auth(6L, "USER")));
        // 管理员可代为管理
        assertDoesNotThrow(() -> AccessGuard.requireOwnerOrAdmin(5L, auth(9L, "ADMIN")));
        // 系统内置资源（owner 为空）仅管理员可改
        assertThrows(AccessDeniedException.class,
                () -> AccessGuard.requireOwnerOrAdmin(null, auth(5L, "USER")));
        assertDoesNotThrow(() -> AccessGuard.requireOwnerOrAdmin(null, auth(9L, "ADMIN")));
    }
}
