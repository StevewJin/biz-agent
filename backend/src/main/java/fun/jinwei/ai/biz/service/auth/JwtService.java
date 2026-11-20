package fun.jinwei.ai.biz.service.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * JWT 服务 - 生成和验证 JWT 令牌
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /** 生成访问令牌 */
    public String generateAccessToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, accessTokenExpiration);
    }

    /** 生成刷新令牌 */
    public String generateRefreshToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, refreshTokenExpiration);
    }

    /** 从令牌中提取用户 ID */
    public Long extractUserId(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /** 从令牌中提取用户名 */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** 从令牌中提取用户角色 */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /** 验证令牌是否有效 */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 获取访问令牌过期时间 (毫秒) */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    // --- 私有方法 ---

    private String buildToken(Long userId, String username, String role, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(Map.of("userId", userId, "role", role))
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
