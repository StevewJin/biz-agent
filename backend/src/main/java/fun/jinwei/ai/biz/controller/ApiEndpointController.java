package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.config.DataContextHolder;
import fun.jinwei.ai.biz.dto.request.ApiEndpointRequest;
import fun.jinwei.ai.biz.dto.response.ApiEndpointResponse;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.security.TeamAccess;
import fun.jinwei.ai.biz.service.apicatalog.ApiEndpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务系统接口管理接口。
 */
@RestController
@RequestMapping("/api/interfaces")
@RequiredArgsConstructor
public class ApiEndpointController {

    private final ApiEndpointService apiEndpointService;
    private final TeamAccess teamAccess;

    @GetMapping
    public ApiResponse<List<ApiEndpointResponse>> list(Authentication auth) {
        return ApiResponse.success(apiEndpointService.list(userId(auth), teamId(auth)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApiEndpointResponse> get(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(apiEndpointService.get(id, userId(auth), teamId(auth)));
    }

    @PostMapping
    public ApiResponse<ApiEndpointResponse> create(@RequestBody ApiEndpointRequest request, Authentication auth) {
        return ApiResponse.success(apiEndpointService.create(request, userId(auth), teamId(auth)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiEndpointResponse> update(@PathVariable Long id,
                                                   @RequestBody ApiEndpointRequest request,
                                                   Authentication auth) {
        return ApiResponse.success(apiEndpointService.update(id, request, userId(auth),
                teamId(auth), AccessGuard.isAdmin(auth)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        apiEndpointService.delete(id, userId(auth), teamId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success();
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> params,
                                                 Authentication auth) {
        apiEndpointService.get(id, userId(auth), teamId(auth));
        String result = apiEndpointService.test(id, params != null ? params : Map.of());
        return ApiResponse.success(Map.of("result", result));
    }

    private Long userId(Authentication auth) {
        return AccessGuard.userId(auth);
    }

    private Long teamId(Authentication auth) {
        // 团队上下文必须成员身份属实，防止伪造 X-Data-Context 越权
        return teamAccess.resolveTeamScope(DataContextHolder.getTeamId(),
                AccessGuard.userId(auth), AccessGuard.isAdmin(auth));
    }
}
