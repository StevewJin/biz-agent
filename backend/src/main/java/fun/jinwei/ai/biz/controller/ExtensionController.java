package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.config.DataContextHolder;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.dto.response.ExtensionResponse;
import fun.jinwei.ai.biz.entity.Extension;
import fun.jinwei.ai.biz.extension.ExtensionInstaller;
import fun.jinwei.ai.biz.extension.ExtensionService;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.security.TeamAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 扩展包接口。
 *
 * <p>归属制：任何用户都能导入声明式扩展包（含 jar 的仍需管理员），
 * 启停/卸载/上架由归属人或团队管理者操作，公开上架需管理员审核。</p>
 */
@RestController
@RequestMapping("/api/extensions")
@RequiredArgsConstructor
public class ExtensionController {

    private final ExtensionService extensionService;
    private final ExtensionInstaller extensionInstaller;
    private final TeamAccess teamAccess;

    /** 扩展包列表：按可见性过滤；管理员可传 publishStatus 查看待审核 */
    @GetMapping
    public ApiResponse<List<ExtensionResponse>> list(
            Authentication auth,
            @RequestParam(required = false) String publishStatus) {
        return ApiResponse.success(extensionService.list(
                userId(auth), teamId(auth), isAdmin(auth), publishStatus));
    }

    /** 工具清单（技能包编辑器使用） */
    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> listTools(Authentication auth) {
        return ApiResponse.success(extensionService.listTools(userId(auth), teamId(auth), isAdmin(auth)));
    }

    /** 前端渲染器清单（前端启动时拉取，公开元数据） */
    @GetMapping("/renderers")
    public ApiResponse<List<Map<String, Object>>> listRenderers() {
        return ApiResponse.success(extensionService.listRenderers());
    }

    @GetMapping("/{extCode}")
    public ApiResponse<ExtensionResponse> detail(@PathVariable String extCode, Authentication auth) {
        return ApiResponse.success(extensionService.detail(extCode, userId(auth), isAdmin(auth)));
    }

    /** 导入扩展包：声明式包所有用户可导入；含 jar 代码的仅管理员 */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importPack(@RequestParam("file") MultipartFile file,
                                                       Authentication auth) {
        Extension extension = extensionInstaller.installFromZip(
                file, userId(auth), teamId(auth), isAdmin(auth));
        return ApiResponse.success(Map.of(
                "extCode", extension.getExtCode(),
                "version", extension.getVersion(),
                "status", extension.getStatus()));
    }

    @PostMapping("/{extCode}/enable")
    public ApiResponse<ExtensionResponse> enable(@PathVariable String extCode, Authentication auth) {
        return ApiResponse.success(extensionService.enable(extCode, userId(auth), isAdmin(auth)));
    }

    @PostMapping("/{extCode}/disable")
    public ApiResponse<ExtensionResponse> disable(@PathVariable String extCode, Authentication auth) {
        return ApiResponse.success(extensionService.disable(extCode, userId(auth), isAdmin(auth)));
    }

    @PostMapping("/{extCode}/reload")
    public ApiResponse<ExtensionResponse> reload(@PathVariable String extCode, Authentication auth) {
        return ApiResponse.success(extensionService.reload(extCode, userId(auth), isAdmin(auth)));
    }

    @DeleteMapping("/{extCode}")
    public ApiResponse<Void> uninstall(@PathVariable String extCode, Authentication auth) {
        extensionService.uninstall(extCode, userId(auth), isAdmin(auth));
        return ApiResponse.success();
    }

    /** 提交上架申请（进入待审核） */
    @PostMapping("/{extCode}/publish")
    public ApiResponse<ExtensionResponse> publish(@PathVariable String extCode, Authentication auth) {
        return ApiResponse.success(extensionService.submitPublish(extCode, userId(auth), isAdmin(auth)));
    }

    /** 撤回上架申请 */
    @PostMapping("/{extCode}/publish/withdraw")
    public ApiResponse<ExtensionResponse> withdraw(@PathVariable String extCode, Authentication auth) {
        return ApiResponse.success(extensionService.withdrawPublish(extCode, userId(auth), isAdmin(auth)));
    }

    /** 管理员审核上架申请 */
    @PostMapping("/{extCode}/review")
    public ApiResponse<ExtensionResponse> review(@PathVariable String extCode,
                                                 @RequestBody Map<String, Object> body,
                                                 Authentication auth) {
        AccessGuard.requireAdmin(auth);
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        return ApiResponse.success(extensionService.review(extCode, approve, reason));
    }

    private Long userId(Authentication auth) {
        return AccessGuard.userId(auth);
    }

    private boolean isAdmin(Authentication auth) {
        return AccessGuard.isAdmin(auth);
    }

    private Long teamId(Authentication auth) {
        return teamAccess.resolveTeamScope(DataContextHolder.getTeamId(), userId(auth), isAdmin(auth));
    }
}
