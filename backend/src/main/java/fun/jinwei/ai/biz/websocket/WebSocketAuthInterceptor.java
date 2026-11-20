package fun.jinwei.ai.biz.websocket;

import fun.jinwei.ai.biz.service.auth.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * WebSocket STOMP 连接认证拦截器
 * 在 CONNECT 帧中验证 JWT token，并设置用户身份
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 从 STOMP CONNECT 帧头中获取 Authorization
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtService.validateToken(token)) {
                    Long userId = jwtService.extractUserId(token);
                    String username = jwtService.extractUsername(token);
                    String role = jwtService.extractRole(token);

                    // 使用自定义 Principal 携带用户信息
                    accessor.setUser(new UserPrincipal(userId, username, role));
                    accessor.getSessionAttributes().put("userId", userId);
                    accessor.getSessionAttributes().put("username", username);
                    log.debug("WebSocket 认证成功: {} (ID: {})", username, userId);
                } else {
                    log.warn("WebSocket JWT 验证失败");
                    throw new SecurityException("无效的认证令牌");
                }
            } else {
                log.warn("WebSocket 连接缺少 Authorization header");
                throw new SecurityException("缺少认证信息");
            }
        }
        return message;
    }

    /**
     * 自定义 Principal，携带 userId 等信息
     */
    public record UserPrincipal(Long userId, String username, String role) implements Principal {
        @Override
        public String getName() {
            return username;
        }
    }
}
