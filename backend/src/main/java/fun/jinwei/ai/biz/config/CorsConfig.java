package fun.jinwei.ai.biz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p>默认不放开任何跨域来源（前后端同源部署或走 Vite 代理）。需要独立前端域名时，
 * 通过 {@code app.security.cors.allowed-origins} 显式配置，禁止使用 {@code *}。</p>
 */
@Configuration
public class CorsConfig {

    @Value("${app.security.cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = parseOrigins(allowedOrigins);
        // 空列表表示仅允许同源
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Data-Context"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    private List<String> parseOrigins(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split(",")) {
            String origin = item.trim();
            if (origin.isEmpty() || "*".equals(origin)) {
                // 明确拒绝通配符与带凭证的组合
                continue;
            }
            result.add(origin);
        }
        return result;
    }
}
