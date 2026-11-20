package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.config.DataContextHolder;
import fun.jinwei.ai.biz.dto.request.DatasourceRequest;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.dto.response.DatasourceResponse;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.security.TeamAccess;
import fun.jinwei.ai.biz.service.datasource.DatasourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务系统数据库（数据源）管理接口。
 */
@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;
    private final TeamAccess teamAccess;

    @GetMapping
    public ApiResponse<List<DatasourceResponse>> list(Authentication auth) {
        return ApiResponse.success(datasourceService.list(userId(auth), teamId(auth)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasourceResponse> get(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(datasourceService.get(id, userId(auth), teamId(auth)));
    }

    @PostMapping
    public ApiResponse<DatasourceResponse> create(@RequestBody DatasourceRequest request, Authentication auth) {
        return ApiResponse.success(datasourceService.create(request, userId(auth), teamId(auth),
                AccessGuard.isAdmin(auth)));
    }

    @PutMapping("/{id}")
    public ApiResponse<DatasourceResponse> update(@PathVariable Long id,
                                                  @RequestBody DatasourceRequest request,
                                                  Authentication auth) {
        return ApiResponse.success(datasourceService.update(id, request, userId(auth),
                teamId(auth), AccessGuard.isAdmin(auth)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        datasourceService.delete(id, userId(auth), teamId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success();
    }

    @PostMapping("/{id}/test")
    public ApiResponse<DatasourceResponse> test(@PathVariable Long id, Authentication auth) {
        datasourceService.get(id, userId(auth), teamId(auth));
        return ApiResponse.success(datasourceService.test(id));
    }

    @PostMapping("/{id}/introspect")
    public ApiResponse<Map<String, Object>> introspect(@PathVariable Long id, Authentication auth) {
        datasourceService.get(id, userId(auth), teamId(auth));
        int count = datasourceService.introspect(id);
        return ApiResponse.success(Map.of("tables", count));
    }

    @GetMapping("/{id}/tables")
    public ApiResponse<List<Map<String, Object>>> tables(@PathVariable Long id, Authentication auth) {
        datasourceService.get(id, userId(auth), teamId(auth));
        return ApiResponse.success(datasourceService.listTables(id));
    }

    @GetMapping("/{id}/tables/{tableName}/columns")
    public ApiResponse<List<Map<String, Object>>> columns(@PathVariable Long id,
                                                          @PathVariable String tableName,
                                                          Authentication auth) {
        datasourceService.get(id, userId(auth), teamId(auth));
        return ApiResponse.success(datasourceService.describeTable(id, tableName));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/preview-query")
    public ApiResponse<List<Map<String, Object>>> previewQuery(@PathVariable Long id,
                                                               @RequestBody Map<String, Object> body,
                                                               Authentication auth) {
        datasourceService.get(id, userId(auth), teamId(auth));
        String sql = body.get("sql") != null ? String.valueOf(body.get("sql")) : "";
        Map<String, Object> params = body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params") : Map.of();
        return ApiResponse.success(datasourceService.previewQuery(id, sql, params));
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
