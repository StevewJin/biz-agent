package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.config.DataContextHolder;
import fun.jinwei.ai.biz.dto.request.SkillActivateRequest;
import fun.jinwei.ai.biz.dto.request.SkillConfigRequest;
import fun.jinwei.ai.biz.dto.request.SkillRequest;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.dto.response.SkillResponse;
import fun.jinwei.ai.biz.entity.SkillConfig;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.security.TeamAccess;
import fun.jinwei.ai.biz.service.skill.SkillManager;
import fun.jinwei.ai.biz.service.skill.SkillPackService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 技能包管理接口。
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillManager skillManager;
    private final SkillPackService skillPackService;
    private final TeamAccess teamAccess;

    /** 获取技能列表 (可传 conversationId 查询激活状态) */
    @GetMapping
    public ApiResponse<List<SkillResponse>> listSkills(
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) String publishStatus,
            Authentication auth) {
        Long convId = conversationId != null ? conversationId : 0L;
        Long userId = AccessGuard.userId(auth);
        Long teamId = teamId(auth);
        boolean admin = AccessGuard.isAdmin(auth);
        var skills = skillManager.listSkills(convId, userId, teamId, admin);
        if (publishStatus != null && !publishStatus.isBlank()) {
            skills = skills.stream()
                    .filter(skill -> publishStatus.equalsIgnoreCase(skill.getPublishStatus()))
                    .toList();
        }
        return ApiResponse.success(skills);
    }

    /** 获取技能详情 */
    @GetMapping("/{id}")
    public ApiResponse<SkillResponse> getSkill(
            @PathVariable Long id,
            @RequestParam(required = false) Long conversationId,
            Authentication auth) {
        Long convId = conversationId != null ? conversationId : 0L;
        return ApiResponse.success(skillManager.getSkill(id, convId,
                AccessGuard.userId(auth), teamId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 预览技能提示词展开结果（占位符解析） */
    @GetMapping("/{id}/preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(skillManager.preview(id, AccessGuard.userId(auth),
                teamId(auth), AccessGuard.isAdmin(auth)));
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> previewDraft(@RequestBody SkillRequest request,
                                                         Authentication auth) {
        return ApiResponse.success(skillPackService.previewDraft(request, AccessGuard.userId(auth),
                teamId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 保存用户级别的技能配置 */
    @PostMapping("/{id}/config")
    public ApiResponse<SkillConfig> configureSkill(
            @PathVariable Long id,
            @RequestBody SkillConfigRequest request,
            Authentication auth) {
        Long userId = AccessGuard.userId(auth);
        return ApiResponse.success(
                skillManager.configureSkill(id, userId, teamId(auth), AccessGuard.isAdmin(auth),
                        request.getConfig() != null ? request.getConfig() : java.util.Map.of()));
    }

    /** 获取用户技能配置 */
    @GetMapping("/{id}/config")
    public ApiResponse<SkillConfig> getSkillConfig(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = AccessGuard.userId(auth);
        Optional<SkillConfig> config = skillManager.getUserSkillConfig(id, userId, teamId(auth), AccessGuard.isAdmin(auth));
        return config.map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "未找到配置"));
    }

    /** 在对话中激活技能 */
    @PostMapping("/{id}/activate")
    public ApiResponse<SkillResponse> activateSkill(
            @PathVariable Long id,
            @RequestParam Long conversationId,
            @RequestBody(required = false) SkillActivateRequest request,
            Authentication auth) {
        return ApiResponse.success(
                skillManager.activateSkill(conversationId, id,
                        request != null ? request.getConfigOverrides() : null,
                        AccessGuard.userId(auth), teamId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 在对话中停用技能 */
    @PostMapping("/{id}/deactivate")
    public ApiResponse<SkillResponse> deactivateSkill(
            @PathVariable Long id,
            @RequestParam Long conversationId,
            Authentication auth) {
        skillManager.deactivateSkill(conversationId, id,
                AccessGuard.userId(auth), teamId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success(skillManager.getSkill(id, conversationId,
                AccessGuard.userId(auth), teamId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 创建技能包 */
    @PostMapping
    public ApiResponse<SkillResponse> createSkill(@RequestBody SkillRequest request, Authentication auth) {
        return ApiResponse.success(skillManager.createSkill(request, AccessGuard.userId(auth), teamId(auth)));
    }

    /** 更新技能包 */
    @PutMapping("/{id}")
    public ApiResponse<SkillResponse> updateSkill(@PathVariable Long id,
                                                  @RequestBody SkillRequest request,
                                                  Authentication auth) {
        skillManager.assertManageable(id, AccessGuard.userId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success(skillManager.updateSkill(id, request));
    }

    /** 删除技能包 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable Long id, Authentication auth) {
        skillManager.assertManageable(id, AccessGuard.userId(auth), AccessGuard.isAdmin(auth));
        skillManager.deleteSkill(id);
        return ApiResponse.success();
    }

    /** 更新技能工具绑定 */
    @PutMapping("/{id}/tool-bindings")
    public ApiResponse<SkillResponse> updateToolBindings(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body,
            Authentication auth) {
        skillManager.assertManageable(id, AccessGuard.userId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success(skillManager.updateToolBindings(id,
                castToStringList(body.get("requiredTools"))));
    }

    /** 导入技能包（zip：skill.json + SKILL.md） */
    @PostMapping("/import")
    public ApiResponse<SkillResponse> importPack(@RequestParam("file") MultipartFile file,
                                                 Authentication auth) {
        return ApiResponse.success(skillPackService.importPack(file,
                AccessGuard.userId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 导出技能包 */
    /** 提交上架申请 */
    @PostMapping("/{id}/publish")
    public ApiResponse<SkillResponse> publish(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(skillManager.submitPublish(
                id, AccessGuard.userId(auth), teamId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 撤回上架申请 */
    @PostMapping("/{id}/publish/withdraw")
    public ApiResponse<SkillResponse> withdrawPublish(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(skillManager.withdrawPublish(
                id, AccessGuard.userId(auth), teamId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 管理员审核上架申请 */
    @PostMapping("/{id}/review")
    public ApiResponse<SkillResponse> review(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body,
                                             Authentication auth) {
        AccessGuard.requireAdmin(auth);
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        return ApiResponse.success(skillManager.reviewPublish(id, approve, reason));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<ByteArrayResource> exportPack(@PathVariable Long id) {
        byte[] data = skillPackService.exportPack(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"skill-pack.zip\"")
                .body(new ByteArrayResource(data));
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> castToStringList(Object obj) {
        if (obj instanceof java.util.List) return (java.util.List<String>) obj;
        return java.util.List.of();
    }

    private Long teamId(Authentication auth) {
        return teamAccess.resolveTeamScope(DataContextHolder.getTeamId(),
                AccessGuard.userId(auth), AccessGuard.isAdmin(auth));
    }
}
