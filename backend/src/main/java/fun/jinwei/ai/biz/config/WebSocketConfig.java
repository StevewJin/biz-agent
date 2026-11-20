package fun.jinwei.ai.biz.config;

import fun.jinwei.ai.biz.websocket.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 配置
 * 客户端连接 /ws/chat，订阅 /user/queue/messages/{conversationId}
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // simple broker 只处理 /queue 和 /topic，/user 由 UserDestinationMessageHandler 处理
        registry.enableSimpleBroker("/queue", "/topic");
        // 客户端发送消息的目标前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 用户目标前缀 (用于 convertAndSendToUser 点对点推送)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP 端点，客户端通过此端点建立 WebSocket 连接
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 注册 JWT 认证拦截器
        registration.interceptors(authInterceptor);
    }
}
