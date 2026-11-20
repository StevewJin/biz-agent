package fun.jinwei.ai.biz.config;

import fun.jinwei.ai.biz.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF (API 服务无需 CSRF 保护)
            .csrf(AbstractHttpConfigurer::disable)
            // 无状态会话
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求授权 (Spring Security 按顺序匹配，先匹配到的规则生效)
            .authorizeHttpRequests(auth -> auth
                // 认证接口放行
                .requestMatchers("/api/auth/**").permitAll()
                // 智能体公开访问端点放行
                .requestMatchers("/api/agent/public/**").permitAll()
                // 文件导出下载：需登录，归属校验在 ExportController 内完成
                .requestMatchers("/api/exports/**").authenticated()
                // 扩展包前端渲染器清单（只读元数据，公开页面也需要）
                .requestMatchers("/api/extensions/renderers").permitAll()
                // 容器探针
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // WebSocket 端点放行 (在握手阶段单独验证)
                .requestMatchers("/ws/**").permitAll()
                // 其余 API 接口需要认证
                .requestMatchers("/api/**").authenticated()
                // 其他所有请求放行 (静态资源 + SPA 路由，由 SpaFallbackConfig 兜底)
                .anyRequest().permitAll())            // 添加 JWT 过滤器 (在 UsernamePasswordAuthenticationFilter 之前)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
