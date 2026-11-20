package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.entity.Agent;
import fun.jinwei.ai.biz.entity.AgentExtension;
import fun.jinwei.ai.biz.entity.AgentSkill;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.security.TeamAccess;
import fun.jinwei.ai.biz.service.agent.AgentService;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import fun.jinwei.ai.biz.service.extension.AgentExtensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentExtensionService agentExtensionService;
    private final EncryptionService encryptionService;
    private final TeamAccess teamAccess;

    @GetMapping
    public ApiResponse<List<Agent>> listAgents(Authentication auth,
                                                @RequestParam(required = false) Long teamId) {
        Long userId = getUserId(auth);
        teamAccess.requireMember(teamId, userId, AccessGuard.isAdmin(auth));
        return ApiResponse.success(agentService.listAgents(userId, teamId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Agent> getAgent(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(requireViewable(id, auth));
    }

    @PostMapping
    public ApiResponse<Agent> createAgent(Authentication auth, @RequestBody Map<String, Object> req) {
        Long teamId = toLong(req.get("teamId"));
        teamAccess.requireMember(teamId, getUserId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success(agentService.createAgent(
                getUserId(auth), teamId,
                (String) req.get("name"), (String) req.get("logo"),
                (String) req.get("description"), (String) req.get("systemPrompt"),
                toLong(req.get("providerId"))));
    }

    @PutMapping("/{id}")
    public ApiResponse<Agent> updateAgent(@PathVariable Long id, @RequestBody Map<String, Object> req,
                                          Authentication auth) {
        requireManageable(id, auth);
        return ApiResponse.success(agentService.updateAgent(id,
                (String) req.get("name"), (String) req.get("logo"),
                (String) req.get("description"), (String) req.get("systemPrompt"),
                toLong(req.get("providerId"))));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAgent(@PathVariable Long id, Authentication auth) {
        requireManageable(id, auth);
        agentService.deleteAgent(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/skills")
    public ApiResponse<List<AgentSkill>> getAgentSkills(@PathVariable Long id, Authentication auth) {
        requireViewable(id, auth);
        return ApiResponse.success(agentService.getAgentSkills(id));
    }

    @PostMapping("/{id}/skills")
    public ApiResponse<AgentSkill> bindSkill(@PathVariable Long id, @RequestBody Map<String, Object> req,
                                             Authentication auth) {
        requireManageable(id, auth);
        Long skillId = ((Number) req.get("skillId")).longValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) req.get("configOverrides");
        return ApiResponse.success(agentService.bindSkill(id, skillId, overrides));
    }

    @DeleteMapping("/{id}/skills/{skillId}")
    public ApiResponse<Void> unbindSkill(@PathVariable Long id, @PathVariable Long skillId,
                                         Authentication auth) {
        requireManageable(id, auth);
        agentService.unbindSkill(id, skillId);
        return ApiResponse.success();
    }

    /** 智能体已绑定的扩展包 */
    @GetMapping("/{id}/extensions")
    public ApiResponse<List<Map<String, Object>>> getAgentExtensions(@PathVariable Long id,
                                                                     Authentication auth) {
        requireViewable(id, auth);
        return ApiResponse.success(agentExtensionService.listBindings(id));
    }

    /** 绑定扩展包 */
    @PostMapping("/{id}/extensions")
    public ApiResponse<AgentExtension> bindExtension(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> req,
                                                     Authentication auth) {
        requireManageable(id, auth);
        Object extCode = req.get("extCode");
        Object extensionId = req.get("extensionId");
        return ApiResponse.success(agentExtensionService.bind(id,
                extCode != null ? String.valueOf(extCode) : null,
                extensionId instanceof Number n ? n.longValue() : null,
                AccessGuard.userId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 解绑扩展包 */
    @DeleteMapping("/{id}/extensions/{extCode}")
    public ApiResponse<Void> unbindExtension(@PathVariable Long id, @PathVariable String extCode,
                                             Authentication auth) {
        requireManageable(id, auth);
        agentExtensionService.unbind(id, extCode);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/regenerate-share-token")
    public ApiResponse<Map<String, String>> regenerateShareToken(@PathVariable Long id,
                                                                 Authentication auth) {
        requireManageable(id, auth);
        String token = agentService.generateShareToken(id);
        return ApiResponse.success(Map.of("shareToken", token));
    }

    @PostMapping("/{id}/generate-aes-key")
    public ApiResponse<Map<String, String>> generateAesKey(@PathVariable Long id, Authentication auth) {
        requireManageable(id, auth);
        String aesKey = encryptionService.generateAesKey();
        agentService.setAesKey(id, aesKey);
        return ApiResponse.success(Map.of("aesKey", aesKey));
    }

    /** 查看权限：归属人 / 团队成员 / 管理员 */
    private Agent requireViewable(Long agentId, Authentication auth) {
        Agent agent = agentService.getAgent(agentId);
        Long userId = AccessGuard.userId(auth);
        if (AccessGuard.isAdmin(auth)) return agent;
        if (agent.getOwner() != null && agent.getOwner().getId().equals(userId)) return agent;
        if (agent.getTeam() != null && teamAccess.isMember(agent.getTeam().getId(), userId)) return agent;
        throw new AccessDeniedException("无权查看该智能体");
    }

    /** 管理权限：归属人 / 团队负责人或团队管理员 / 管理员 */
    private void requireManageable(Long agentId, Authentication auth) {
        Agent agent = agentService.getAgent(agentId);
        Long userId = AccessGuard.userId(auth);
        if (AccessGuard.isAdmin(auth)) return;
        if (agent.getOwner() != null && agent.getOwner().getId().equals(userId)) return;
        if (agent.getTeam() != null && teamAccess.isTeamManager(agent.getTeam().getId(), userId)) return;
        throw new AccessDeniedException("无权修改该智能体");
    }

    private Long getUserId(Authentication auth) {
        return AccessGuard.userId(auth);
    }

    private Long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}
