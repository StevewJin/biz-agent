package fun.jinwei.ai.biz.service.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.dto.request.SkillRequest;
import fun.jinwei.ai.biz.dto.response.SkillResponse;
import fun.jinwei.ai.biz.entity.ConversationSkill;
import fun.jinwei.ai.biz.entity.Skill;
import fun.jinwei.ai.biz.entity.SkillConfig;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.repository.ConversationSkillRepository;
import fun.jinwei.ai.biz.repository.SkillRepository;
import fun.jinwei.ai.biz.security.ResourceVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 技能包管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillManager {

    private final SkillRegistry skillRegistry;
    private final SkillPromptResolver promptResolver;
    private final ConversationSkillRepository conversationSkillRepository;
    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;
    private final ConversationRepository conversationRepository;
    private final ResourceVisibility visibility;

    /** 获取当前用户可见的技能 (内置 + 本人；管理员可见全部) */
    public List<SkillResponse> listSkills(Long conversationId, Long userId, Long teamId, boolean admin) {
        List<Skill> allSkills = skillRegistry.getAllDbSkills().stream()
                .filter(skill -> canView(skill, userId, teamId, admin))
                .toList();
        List<ConversationSkill> activeSkills = conversationSkillRepository
                .findByConversationId(conversationId);
        Set<Long> activeSkillIds = activeSkills.stream()
                .filter(ConversationSkill::getEnabled)
                .map(cs -> cs.getSkill().getId())
                .collect(Collectors.toSet());
        Map<Long, Map<String, Object>> overridesMap = activeSkills.stream()
                .filter(cs -> cs.getConfigOverrides() != null && !cs.getConfigOverrides().isBlank())
                .collect(Collectors.toMap(
                        cs -> cs.getSkill().getId(),
                        cs -> parseConfigOverrides(cs.getConfigOverrides()),
                        (a, b) -> a));

        return allSkills.stream()
                .map(skill -> toSkillResponse(skill, activeSkillIds.contains(skill.getId()),
                        overridesMap.get(skill.getId())))
                .collect(Collectors.toList());
    }

    /** 获取单个技能详情 (含对话激活状态) */
    public SkillResponse getSkill(Long skillId, Long conversationId, Long userId, Long teamId, boolean admin) {
        Skill skill = requireViewable(skillId, userId, teamId, admin);
        ConversationSkill cs = conversationSkillRepository
                .findByConversationIdAndSkillId(conversationId, skillId).orElse(null);
        boolean active = cs != null && cs.getEnabled();
        Map<String, Object> overrides = cs != null ? parseConfigOverrides(cs.getConfigOverrides()) : null;
        return toSkillResponse(skill, active, overrides);
    }

    @Transactional
    public SkillResponse activateSkill(Long conversationId, Long skillId,
                                       Map<String, Object> configOverrides,
                                       Long userId, Long teamId, boolean admin) {
        assertConversationOwner(conversationId, userId, admin);
        requireViewable(skillId, userId, teamId, admin);
        skillRegistry.activateSkill(conversationId, skillId, configOverrides);
        log.info("技能已激活: conversationId={}, skillId={}", conversationId, skillId);
        return getSkill(skillId, conversationId, userId, teamId, admin);
    }

    @Transactional
    public void deactivateSkill(Long conversationId, Long skillId, Long userId, Long teamId, boolean admin) {
        assertConversationOwner(conversationId, userId, admin);
        requireViewable(skillId, userId, teamId, admin);
        skillRegistry.deactivateSkill(conversationId, skillId);
        log.info("技能已停用: conversationId={}, skillId={}", conversationId, skillId);
    }

    @Transactional
    public SkillConfig configureSkill(Long skillId, Long userId, Long teamId, boolean admin,
                                      Map<String, Object> config) {
        requireViewable(skillId, userId, teamId, admin);
        log.info("技能配置已保存: skillId={}, userId={}", skillId, userId);
        return skillRegistry.saveUserSkillConfig(skillId, userId, config);
    }

    public Optional<SkillConfig> getUserSkillConfig(Long skillId, Long userId, Long teamId, boolean admin) {
        requireViewable(skillId, userId, teamId, admin);
        return skillRegistry.getUserSkillConfig(skillId, userId);
    }

    /** 预览技能提示词展开结果 */
    public Map<String, Object> preview(Long skillId, Long userId, Long teamId, boolean admin) {
        Skill skill = requireViewable(skillId, userId, teamId, admin);
        return promptResolver.preview(skill, userId, teamId, admin);
    }

    /** 技能可见性：内置 / 本人 / 团队 / 已审核公开 */
    public boolean canView(Skill skill, Long userId, Long teamId, boolean admin) {
        return visibility.isVisible(skill.getOwnerId(), skill.getTeamId(),
                skill.getVisibility(), skill.getPublishStatus(), userId, admin);
    }

    /** 技能可见性校验，返回技能实体 */
    public Skill requireViewable(Long skillId, Long userId, Long teamId, boolean admin) {
        Skill skill = skillRegistry.getDbSkill(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));
        if (!canView(skill, userId, teamId, admin)) {
            throw new AccessDeniedException("无权访问该技能包");
        }
        return skill;
    }

    /** 提交上架申请 */
    @Transactional
    public SkillResponse submitPublish(Long skillId, Long userId, Long teamId, boolean admin) {
        Skill skill = requireManageableSkill(skillId, userId, teamId, admin);
        skill.setVisibility(ResourceVisibility.VISIBILITY_PUBLIC);
        skill.setPublishStatus(ResourceVisibility.STATUS_PENDING);
        skill.setRejectReason(null);
        skillRepository.save(skill);
        promptResolver.invalidate();
        log.info("技能包提交上架: skillId={} by {}", skillId, userId);
        return toSkillResponse(skill, false, null);
    }

    /** 撤回上架申请 */
    @Transactional
    public SkillResponse withdrawPublish(Long skillId, Long userId, Long teamId, boolean admin) {
        Skill skill = requireManageableSkill(skillId, userId, teamId, admin);
        skill.setVisibility(ResourceVisibility.defaultVisibility(skill.getTeamId()));
        skill.setPublishStatus(ResourceVisibility.STATUS_DRAFT);
        skill.setRejectReason(null);
        skillRepository.save(skill);
        promptResolver.invalidate();
        return toSkillResponse(skill, false, null);
    }

    /** 管理员审核上架申请 */
    @Transactional
    public SkillResponse reviewPublish(Long skillId, boolean approve, String reason) {
        Skill skill = skillRegistry.getDbSkill(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));
        if (!ResourceVisibility.STATUS_PENDING.equalsIgnoreCase(skill.getPublishStatus())) {
            throw new IllegalArgumentException("该技能包没有待审核的上架申请");
        }
        if (approve) {
            skill.setVisibility(ResourceVisibility.VISIBILITY_PUBLIC);
            skill.setPublishStatus(ResourceVisibility.STATUS_APPROVED);
            skill.setRejectReason(null);
            skill.setPublishedAt(java.time.LocalDateTime.now());
        } else {
            skill.setVisibility(ResourceVisibility.defaultVisibility(skill.getTeamId()));
            skill.setPublishStatus(ResourceVisibility.STATUS_REJECTED);
            skill.setRejectReason(reason != null && !reason.isBlank() ? reason : "未通过审核");
        }
        skillRepository.save(skill);
        promptResolver.invalidate();
        log.info("技能包审核完成: skillId={} approve={}", skillId, approve);
        return toSkillResponse(skill, false, null);
    }

    /** 可管理校验（归属人 / 团队管理者 / 管理员；内置仅管理员） */
    public Skill requireManageableSkill(Long skillId, Long userId, Long teamId, boolean admin) {
        Skill skill = skillRegistry.getDbSkill(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));
        if (!visibility.canPublish(skill.getOwnerId(), skill.getTeamId(), userId, admin)) {
            throw new AccessDeniedException("仅归属人或团队管理者可执行此操作");
        }
        return skill;
    }

    /** 会话归属校验，防止在他人会话上激活技能 */
    private void assertConversationOwner(Long conversationId, Long userId, boolean admin) {
        if (conversationId == null || conversationId == 0L) {
            return;
        }
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            boolean owner = conversation.getUser() != null
                    && conversation.getUser().getId().equals(userId);
            if (!admin && !owner) {
                throw new AccessDeniedException("无权操作该会话");
            }
        });
    }

    @Transactional
    public SkillResponse createSkill(SkillRequest request, Long ownerId, Long teamId) {
        if (request.getName() == null || !request.getName().matches("[a-zA-Z][a-zA-Z0-9_\\-]{1,99}")) {
            throw new IllegalArgumentException("技能编码需以字母开头，仅允许字母、数字、下划线和中划线");
        }
        if (skillRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("技能编码已存在: " + request.getName());
        }
        Skill skill = Skill.builder()
                .name(request.getName())
                .displayName(defaultIfBlank(request.getDisplayName(), request.getName()))
                .description(defaultIfBlank(request.getDescription(), ""))
                .category(defaultIfBlank(request.getCategory(), "general"))
                .promptMd(defaultIfBlank(request.getPromptMd(), ""))
                .configSchema(request.getConfigSchema() != null ? toJson(request.getConfigSchema()) : null)
                .resourcesJson(toResourcesJson(request))
                .keywords(request.getKeywords())
                .triggerMode(defaultIfBlank(request.getTriggerMode(), "auto"))
                .schemaMode(defaultIfBlank(request.getSchemaMode(), "inline"))
                .skillVersion(defaultIfBlank(request.getSkillVersion(), "1.0.0"))
                .packSource("manual")
                .ownerId(ownerId)
                .teamId(teamId)
                .visibility(ResourceVisibility.defaultVisibility(teamId))
                .publishStatus(ResourceVisibility.STATUS_DRAFT)
                .icon(defaultIfBlank(request.getIcon(), "zap"))
                .iconColor(request.getIconColor())
                .enabled(true)
                .build();
        skill = skillRepository.save(skill);
        promptResolver.invalidate();
        log.info("技能包创建成功: {} ({})", skill.getDisplayName(), skill.getName());
        return toSkillResponse(skill, false, null);
    }

    /** 技能包管理权限校验：内置技能包仅管理员，自定义技能包仅归属人/管理员 */
    public void assertManageable(Long skillId, Long userId, boolean admin) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));
        if (skill.getOwnerId() == null) {
            if (!admin) {
                throw new org.springframework.security.access.AccessDeniedException("系统内置技能包仅管理员可修改");
            }
            return;
        }
        if (!admin && !skill.getOwnerId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("无权修改该技能包");
        }
    }

    /** 供导入时校验同名技能包是否允许覆盖 */
    public void assertImportable(Skill existing, Long userId, boolean admin) {
        if (existing == null) return;
        if (existing.getOwnerId() == null) {
            if (!admin) {
                throw new org.springframework.security.access.AccessDeniedException("系统内置技能包仅管理员可覆盖");
            }
            return;
        }
        if (!admin && !existing.getOwnerId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("同名技能包属于其他用户，无法覆盖");
        }
    }

    @Transactional
    public SkillResponse updateSkill(Long skillId, SkillRequest request) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));
        if (request.getDisplayName() != null) skill.setDisplayName(request.getDisplayName());
        if (request.getDescription() != null) skill.setDescription(request.getDescription());
        if (request.getCategory() != null) skill.setCategory(request.getCategory());
        if (request.getPromptMd() != null) skill.setPromptMd(request.getPromptMd());
        if (request.getConfigSchema() != null) skill.setConfigSchema(toJson(request.getConfigSchema()));
        if (request.getResources() != null || request.getRequiredTools() != null) {
            skill.setResourcesJson(toResourcesJson(request, skill.getResourcesJson()));
        }
        if (request.getKeywords() != null) skill.setKeywords(request.getKeywords());
        if (request.getTriggerMode() != null) skill.setTriggerMode(request.getTriggerMode());
        if (request.getSchemaMode() != null) skill.setSchemaMode(request.getSchemaMode());
        if (request.getSkillVersion() != null) skill.setSkillVersion(request.getSkillVersion());
        if (request.getIcon() != null) skill.setIcon(request.getIcon());
        if (request.getIconColor() != null) skill.setIconColor(request.getIconColor());
        if (request.getEnabled() != null) skill.setEnabled(request.getEnabled());
        skill = skillRepository.save(skill);
        promptResolver.invalidate();
        log.info("技能包已更新: skillId={}", skillId);
        return toSkillResponse(skill, false, null);
    }

    @Transactional
    public void deleteSkill(Long skillId) {
        skillRepository.deleteById(skillId);
        promptResolver.invalidate();
        log.info("技能包已删除: skillId={}", skillId);
    }

    /** 更新技能声明的工具白名单（与 resources_json.tools 同步） */
    @Transactional
    public SkillResponse updateToolBindings(Long skillId, List<String> requiredTools) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));
        SkillResources current = SkillResources.parse(skill.getResourcesJson(), objectMapper);
        SkillResources updated = new SkillResources(
                current.datasources(), current.interfaces(), current.extensions(),
                requiredTools != null ? requiredTools : List.of());
        skill.setResourcesJson(updated.toJson(objectMapper));
        skillRepository.save(skill);
        promptResolver.invalidate();
        return toSkillResponse(skill, false, null);
    }

    private SkillResponse toSkillResponse(Skill skill, boolean active, Map<String, Object> overrides) {
        SkillResources resources = SkillResources.parse(skill.getResourcesJson(), objectMapper);
        Map<String, Object> resourcesMap = new LinkedHashMap<>();
        resourcesMap.put("datasources", resources.datasources());
        resourcesMap.put("interfaces", resources.interfaces());
        resourcesMap.put("extensions", resources.extensions());
        resourcesMap.put("tools", resources.tools());

        return SkillResponse.builder()
                .id(skill.getId())
                .name(skill.getName())
                .displayName(skill.getDisplayName())
                .description(skill.getDescription())
                .category(skill.getCategory())
                .icon(skill.getIcon())
                .iconColor(skill.getIconColor())
                .promptMd(skill.getPromptMd())
                .configSchema(skillRegistry.parseJsonToMap(skill.getConfigSchema()))
                .resources(resourcesMap)
                .requiredTools(resources.tools())
                .keywords(skill.getKeywords())
                .triggerMode(skill.getTriggerMode())
                .skillVersion(skill.getSkillVersion())
                .packSource(skill.getPackSource())
                .schemaMode(skill.getSchemaMode())
                .ownerId(skill.getOwnerId())
                .teamId(skill.getTeamId())
                .visibility(skill.getVisibility())
                .publishStatus(skill.getPublishStatus())
                .rejectReason(skill.getRejectReason())
                .publishedAt(skill.getPublishedAt())
                .active(active)
                .configOverrides(overrides)
                .createdAt(skill.getCreatedAt())
                .build();
    }

    private String toResourcesJson(SkillRequest request) {
        return toResourcesJson(request, null);
    }

    /**
     * 合并请求中的资源引用与工具白名单。
     * 更新场景下未传 resources 时保留已有声明，避免局部更新丢失资源。
     */
    private String toResourcesJson(SkillRequest request, String existingJson) {
        SkillResources resources = existingJson != null
                ? SkillResources.parse(existingJson, objectMapper)
                : SkillResources.empty();
        if (request.getResources() != null) {
            resources = new SkillResources(
                    request.getResources().get("datasources"),
                    request.getResources().get("interfaces"),
                    request.getResources().get("extensions"),
                    request.getResources().get("tools"));
        }
        if (request.getRequiredTools() != null && !request.getRequiredTools().isEmpty()) {
            resources = resources.merge(new SkillResources(List.of(), List.of(), List.of(),
                    request.getRequiredTools()));
        }
        return resources.toJson(objectMapper);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigOverrides(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

