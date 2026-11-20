package fun.jinwei.ai.biz.config;

import fun.jinwei.ai.biz.service.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DataContextFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            Long userId = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtService.validateToken(token)) {
                    userId = jwtService.extractUserId(token);
                }
            }
            if (userId == null) {
                userId = (Long) request.getAttribute("userId");
            }

            String contextHeader = request.getHeader("X-Data-Context");
            Long teamId = null;
            if (contextHeader != null && contextHeader.startsWith("team:")) {
                teamId = Long.parseLong(contextHeader.substring(5));
            }

            if (userId != null) {
                DataContextHolder.set(new DataContextHolder.DataContext(userId, teamId));
            }
            filterChain.doFilter(request, response);
        } finally {
            DataContextHolder.clear();
        }
    }
}
