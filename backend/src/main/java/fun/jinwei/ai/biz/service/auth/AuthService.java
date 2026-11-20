package fun.jinwei.ai.biz.service.auth;

import fun.jinwei.ai.biz.dto.request.LoginRequest;
import fun.jinwei.ai.biz.dto.request.RegisterRequest;
import fun.jinwei.ai.biz.dto.response.AuthResponse;
import fun.jinwei.ai.biz.entity.User;
import fun.jinwei.ai.biz.exception.BizException;
import fun.jinwei.ai.biz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务 - 注册、登录、刷新令牌
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** 用户注册 */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名和邮箱是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BizException(400, "用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BizException(400, "邮箱已注册");
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("user")
                .build();
        user = userRepository.save(user);

        // 生成令牌
        return buildAuthResponse(user);
    }

    /** 用户登录 */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }

        return buildAuthResponse(user);
    }

    /** 刷新令牌 */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new BizException(401, "刷新令牌无效或已过期");
        }

        Long userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(401, "用户不存在"));

        return buildAuthResponse(user);
    }

    /** 构建认证响应 */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .build())
                .build();
    }
}
