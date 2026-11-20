package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.request.LoginRequest;
import fun.jinwei.ai.biz.dto.request.RegisterRequest;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.dto.response.AuthResponse;
import fun.jinwei.ai.biz.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用户注册 */
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    /** 用户登录 */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /** 刷新令牌 */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refreshToken(request.getRefreshToken()));
    }

    /** 刷新令牌请求体 */
    @lombok.Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
