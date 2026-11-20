package fun.jinwei.ai.biz.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SpaFallbackConfig.class);

    /**
     * 静态资源缓存策略：
     * 带 hash 的 /assets/** 长缓存；index.html 禁止缓存，避免发版后仍加载旧 bundle。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(31536000);

        registry.addResourceHandler("/index.html", "/favicon.*", "/icons.*")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(org.springframework.http.CacheControl.noStore());
    }

    /** SPA fallback Filter: 无后缀的非 API 路径转发到 /index.html */
    @Bean
    public FilterRegistrationBean<SpaFallbackFilter> spaFallbackFilter() {
        FilterRegistrationBean<SpaFallbackFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new SpaFallbackFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.LOWEST_PRECEDENCE);
        reg.setDispatcherTypes(DispatcherType.REQUEST);
        return reg;
    }

    static class SpaFallbackFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            String path = req.getRequestURI();

            if (path.startsWith("/api/") || path.startsWith("/ws/")
                    || path.startsWith("/actuator")
                    || path.contains(".")) {
                chain.doFilter(request, response);
                return;
            }

            log.debug("SPA fallback: {} → /index.html", path);
            req.getRequestDispatcher("/index.html").forward(request, response);
        }
    }
}
