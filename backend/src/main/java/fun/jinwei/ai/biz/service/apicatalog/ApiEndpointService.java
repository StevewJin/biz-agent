package fun.jinwei.ai.biz.service.apicatalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.dto.request.ApiEndpointRequest;
import fun.jinwei.ai.biz.dto.response.ApiEndpointResponse;
import fun.jinwei.ai.biz.entity.ApiEndpoint;
import fun.jinwei.ai.biz.repository.ApiEndpointRepository;
import fun.jinwei.ai.biz.security.OutboundPolicy;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务系统接口管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiEndpointService {

    private final ApiEndpointRepository repository;
    private final EncryptionService encryptionService;
    private final ApiExecutor apiExecutor;
    private final OutboundPolicy outboundPolicy;
    private final ObjectMapper objectMapper;

    public List<ApiEndpointResponse> list(Long userId, Long teamId) {
        return repository.findAllByOrderByIdDesc().stream()
                .filter(ep -> canView(ep, userId, teamId))
                .map(this::toResponse)
                .sorted(Comparator.comparing(ApiEndpointResponse::getId).reversed())
                .toList();
    }

    public ApiEndpointResponse get(Long id, Long userId, Long teamId) {
        ApiEndpoint ep = require(id);
        assertAccess(ep, userId, teamId);
        return toResponse(ep);
    }

    @Transactional
    public ApiEndpointResponse create(ApiEndpointRequest req, Long userId, Long teamId) {
        validate(req, null);
        ApiEndpoint ep = ApiEndpoint.builder()
                .apiCode(req.getApiCode().trim())
                .apiName(req.getApiName())
                .baseUrl(trimTrailingSlash(req.getBaseUrl()))
                .path(defaultIfBlank(req.getPath(), ""))
                .httpMethod(defaultIfBlank(req.getHttpMethod(), "GET").toUpperCase())
                .authType(defaultIfBlank(req.getAuthType(), "none"))
                .authConfigEncrypted(encryptAuthConfig(req.getAuthConfig()))
                .defaultHeaders(toJson(req.getDefaultHeaders()))
                .paramSchema(req.getParamSchema())
                .responseSchema(req.getResponseSchema())
                .requestExample(req.getRequestExample())
                .timeoutMs(req.getTimeoutMs() != null ? req.getTimeoutMs() : 30000)
                .ownerId(userId)
                .teamId(teamId)
                .visibility(defaultIfBlank(req.getVisibility(), teamId != null ? "team" : "personal"))
                .description(req.getDescription())
                .enabled(req.getEnabled() == null || req.getEnabled())
                .build();
        ep = repository.save(ep);
        log.info("接口已创建: {} {}", ep.getHttpMethod(), ep.getApiCode());
        return toResponse(ep);
    }

    @Transactional
    public ApiEndpointResponse update(Long id, ApiEndpointRequest req, Long userId, Long teamId, boolean admin) {
        ApiEndpoint ep = require(id);
        assertManage(ep, userId, admin);
        if (req.getApiCode() != null && !req.getApiCode().isBlank() && !req.getApiCode().equals(ep.getApiCode())) {
            if (repository.findByApiCode(req.getApiCode()).isPresent()) {
                throw new IllegalArgumentException("接口编码已存在: " + req.getApiCode());
            }
            ep.setApiCode(req.getApiCode().trim());
        }
        if (req.getApiName() != null) ep.setApiName(req.getApiName());
        if (req.getBaseUrl() != null) ep.setBaseUrl(trimTrailingSlash(req.getBaseUrl()));
        if (req.getPath() != null) ep.setPath(req.getPath());
        if (req.getHttpMethod() != null) ep.setHttpMethod(req.getHttpMethod().toUpperCase());
        if (req.getAuthType() != null) ep.setAuthType(req.getAuthType());
        if (req.getAuthConfig() != null) ep.setAuthConfigEncrypted(encryptAuthConfig(req.getAuthConfig()));
        if (req.getDefaultHeaders() != null) ep.setDefaultHeaders(toJson(req.getDefaultHeaders()));
        if (req.getParamSchema() != null) ep.setParamSchema(req.getParamSchema());
        if (req.getResponseSchema() != null) ep.setResponseSchema(req.getResponseSchema());
        if (req.getRequestExample() != null) ep.setRequestExample(req.getRequestExample());
        if (req.getTimeoutMs() != null) ep.setTimeoutMs(req.getTimeoutMs());
        if (req.getVisibility() != null) ep.setVisibility(req.getVisibility());
        if (req.getDescription() != null) ep.setDescription(req.getDescription());
        if (req.getEnabled() != null) ep.setEnabled(req.getEnabled());
        ep = repository.save(ep);
        log.info("接口已更新: {}", ep.getApiCode());
        return toResponse(ep);
    }

    @Transactional
    public void delete(Long id, Long userId, Long teamId, boolean admin) {
        ApiEndpoint ep = require(id);
        assertManage(ep, userId, admin);
        repository.delete(ep);
        log.info("接口已删除: {}", ep.getApiCode());
    }

    /** 试调接口 */
    public String test(Long id, Map<String, Object> params) {
        ApiEndpoint ep = require(id);
        return apiExecutor.invoke(ep, params);
    }

    public Optional<ApiEndpoint> findVisibleByCode(String apiCode, Long userId, Long teamId) {
        return repository.findByApiCode(apiCode)
                .filter(ep -> Boolean.TRUE.equals(ep.getEnabled()))
                .filter(ep -> canView(ep, userId, teamId));
    }

    public ApiEndpoint requireByCode(String apiCode) {
        return repository.findByApiCode(apiCode)
                .orElseThrow(() -> new IllegalArgumentException("接口不存在: " + apiCode));
    }

    public ApiEndpoint require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("接口不存在: " + id));
    }

    public boolean canView(ApiEndpoint ep, Long userId, Long teamId) {
        if (userId != null && userId.equals(ep.getOwnerId())) return true;
        if ("public".equalsIgnoreCase(ep.getVisibility())) return true;
        return teamId != null && teamId.equals(ep.getTeamId());
    }

    public void assertAccess(ApiEndpoint ep, Long userId, Long teamId) {
        if (!canView(ep, userId, teamId)) {
            throw new org.springframework.security.access.AccessDeniedException("无权访问该接口: " + ep.getApiCode());
        }
    }

    /** 修改/删除权限：仅归属人或管理员 */
    public void assertManage(ApiEndpoint ep, Long userId, boolean admin) {
        if (!admin && (userId == null || !userId.equals(ep.getOwnerId()))) {
            throw new org.springframework.security.access.AccessDeniedException("无权修改该接口: " + ep.getApiCode());
        }
    }

    private void validate(ApiEndpointRequest req, Long excludeId) {
        if (req.getApiCode() == null || req.getApiCode().isBlank()) {
            throw new IllegalArgumentException("接口编码不能为空");
        }
        if (!req.getApiCode().matches("[a-zA-Z][a-zA-Z0-9_\\-]{1,99}")) {
            throw new IllegalArgumentException("接口编码需以字母开头，仅允许字母、数字、下划线和中划线");
        }
        if (req.getApiName() == null || req.getApiName().isBlank()) {
            throw new IllegalArgumentException("接口名称不能为空");
        }
        if (req.getBaseUrl() == null || req.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("服务地址不能为空");
        }
        if (!req.getBaseUrl().startsWith("http://") && !req.getBaseUrl().startsWith("https://")) {
            throw new IllegalArgumentException("服务地址必须以 http:// 或 https:// 开头");
        }
        // SSRF 防护：禁止回环/云元数据/未放行的内网地址
        outboundPolicy.assertHttpUrlAllowed(
                trimTrailingSlash(req.getBaseUrl()) + defaultIfBlank(req.getPath(), ""), "服务地址");
        repository.findByApiCode(req.getApiCode()).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new IllegalArgumentException("接口编码已存在: " + req.getApiCode());
            }
        });
    }

    private String encryptAuthConfig(Map<String, Object> authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return null;
        }
        try {
            return encryptionService.encrypt(objectMapper.writeValueAsString(authConfig));
        } catch (Exception e) {
            throw new IllegalArgumentException("鉴权配置序列化失败: " + e.getMessage());
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> parseHeaders(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String trimTrailingSlash(String url) {
        if (url == null) return null;
        String result = url.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private ApiEndpointResponse toResponse(ApiEndpoint ep) {
        return ApiEndpointResponse.builder()
                .id(ep.getId())
                .apiCode(ep.getApiCode())
                .apiName(ep.getApiName())
                .baseUrl(ep.getBaseUrl())
                .path(ep.getPath())
                .httpMethod(ep.getHttpMethod())
                .authType(ep.getAuthType())
                .authMasked(ep.getAuthConfigEncrypted() != null ? "******" : null)
                .defaultHeaders(parseHeaders(ep.getDefaultHeaders()))
                .paramSchema(ep.getParamSchema())
                .responseSchema(ep.getResponseSchema())
                .requestExample(ep.getRequestExample())
                .timeoutMs(ep.getTimeoutMs())
                .ownerId(ep.getOwnerId())
                .teamId(ep.getTeamId())
                .visibility(ep.getVisibility())
                .description(ep.getDescription())
                .enabled(ep.getEnabled())
                .createdAt(ep.getCreatedAt())
                .updatedAt(ep.getUpdatedAt())
                .build();
    }
}

