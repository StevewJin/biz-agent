package fun.jinwei.ai.biz.service.agent;

import fun.jinwei.ai.biz.entity.Agent;
import fun.jinwei.ai.biz.entity.AgentExtension;
import fun.jinwei.ai.biz.entity.AgentSkill;
import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.entity.Skill;
import fun.jinwei.ai.biz.entity.SkillConfig;
import fun.jinwei.ai.biz.entity.Team;
import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.repository.AgentExtensionRepository;
import fun.jinwei.ai.biz.repository.AgentSkillRepository;
import fun.jinwei.ai.biz.repository.SkillConfigRepository;
import fun.jinwei.ai.biz.service.apicatalog.ApiEndpointService;
import fun.jinwei.ai.biz.service.datasource.DatasourceService;
import fun.jinwei.ai.biz.service.skill.SkillPromptResolver;
import fun.jinwei.ai.biz.service.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 运行计划构建器。
 *
 * <p>把「智能体 → 技能包 → 资源 → 工具」展开为一次运行所需的系统提示词与工具白名单，
 * 根据用户消息挑出命中触发的技能包，汇总系统提示词、工具白名单与资源作用域。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunPlanner {

    private final SkillRegistry skillRegistry;
    private final SkillPromptResolver promptResolver;
    private final SkillConfigRepository skillConfigRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final AgentExtensionRepository agentExtensionRepository;
    private final ExtensionRuntime extensionRuntime;
    private final DatasourceService datasourceService;
    private final ApiEndpointService apiEndpointService;
    private final SystemPromptBuilder systemPromptBuilder;

    @Value("${app.agent.max-tools:40}")
    private int maxTools;

    public AgentRunPlan plan(Conversation conversation, Agent agent, String userMessage, String username) {
        boolean anonymous = username == null || username.isBlank();
        Long userId = conversation.getUser() != null ? conversation.getUser().getId() : null;
        Long teamId = resolveTeamId(conversation, agent);

        List<Skill> candidateSkills = collectSkills(conversation, agent);
        Set<Long> conversationActiveIds = skillRegistry
                .getActiveSkillsForConversation(conversation.getId()).stream()
                .map(Skill::getId)
                .collect(Collectors.toSet());

        List<Skill> selected = candidateSkills.stream()
                .filter(skill -> isTriggered(skill, userMessage, conversationActiveIds))
                .toList();

        Set<String> toolKeys = new LinkedHashSet<>();
        Set<String> datasourceCodes = new LinkedHashSet<>();
        Set<String> apiCodes = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> toolSkillNames = new LinkedHashMap<>();
        Map<Long, SkillConfig> configMap = userConfigMap(userId);

        StringBuilder skillsPrompt = new StringBuilder();
        if (!candidateSkills.isEmpty()) {
            skillsPrompt.append("\n\n## 可用业务技能\n");
            for (Skill skill : candidateSkills) {
                skillsPrompt.append("- **").append(skill.getDisplayName()).append("**");
                if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                    skillsPrompt.append(": ").append(skill.getDescription());
                }
                skillsPrompt.append("\n");
            }
        }

        if (!selected.isEmpty()) {
            skillsPrompt.append("\n## 当前任务适用的技能（请严格遵循）\n");
            for (Skill skill : selected) {
                SkillPromptResolver.ResolvedSkill resolved =
                        promptResolver.resolve(skill, userId, teamId, false);
                // 匿名访问：技能包含需登录才能使用的工具时，不下发该技能（避免会泄露表结构等业务信息）
                if (anonymous && hasRestrictedTool(resolved.toolKeys())) {
                    log.info("匿名会话跳过技能: {} (包含受限工具)", skill.getName());
                    continue;
                }
                skillsPrompt.append("\n### ").append(resolved.title()).append("\n");
                skillsPrompt.append(resolved.prompt()).append("\n");
                // 记录「工具 → 触发它的技能包」，供前端工具卡片展示归属
                resolved.toolKeys().forEach(key ->
                        toolSkillNames.putIfAbsent(key, skill.getDisplayName()));
                toolKeys.addAll(resolved.toolKeys());
                datasourceCodes.addAll(resolved.datasourceCodes());
                apiCodes.addAll(resolved.apiCodes());
                warnings.addAll(resolved.warnings());

                String configPrompt = skillRegistry.buildSkillConfigPrompt(
                        skill, configMap.get(skill.getId()), null);
                if (!configPrompt.isEmpty()) {
                    skillsPrompt.append(configPrompt).append("\n");
                }
            }
        } else if (!candidateSkills.isEmpty()) {
            skillsPrompt.append("\n(以上技能均可使用，根据用户需求自行选择合适的技能)\n");
        }

        // 智能体直接绑定的扩展包
        if (agent != null) {
            List<AgentExtension> bindings = agentExtensionRepository.findByAgentId(agent.getId());
            for (AgentExtension binding : bindings) {
                if (!Boolean.TRUE.equals(binding.getEnabled())) continue;
                extensionRuntime.all().stream()
                        .filter(loaded -> loaded.entity().getId().equals(binding.getExtensionId()))
                        .findFirst()
                        .ifPresent(loaded -> {
                            for (ExtensionManifest.ToolDef def : loaded.manifest().getTools()) {
                                if (!Boolean.FALSE.equals(def.getEnabled())) {
                                    toolKeys.add(ExtensionRuntime.toolKey(
                                            loaded.entity().getExtCode(), def.getCode()));
                                }
                            }
                        });
            }
        }

        String basePrompt = agent != null && agent.getSystemPrompt() != null
                && !agent.getSystemPrompt().isBlank()
                ? agent.getSystemPrompt() + skillsPrompt
                : systemPromptBuilder.build(
                        agent != null ? null : conversation.getSystemPrompt(),
                        skillsPrompt.toString(),
                        username);

        // 注入当前用户可访问的业务资源清单（供未显式引用具体资源的技能使用）
        if (!anonymous) {
            basePrompt = basePrompt + buildAvailableResources(userId, teamId, toolKeys);
        }

        if (agent != null) {
            basePrompt = appendUserInfo(basePrompt, username);
        }

        if (!warnings.isEmpty()) {
            log.warn("技能资源配置告警 conversationId={}: {}", conversation.getId(), warnings);
        }

        List<String> limitedTools = limitTools(toolKeys);
        if (limitedTools.size() < toolKeys.size()) {
            log.warn("工具数量超过上限 {}，已截断: conversationId={}", maxTools, conversation.getId());
        }

        return new AgentRunPlan(
                basePrompt,
                limitedTools,
                Set.copyOf(datasourceCodes),
                Set.copyOf(apiCodes),
                anonymous,
                selected.stream().map(Skill::getDisplayName).toList(),
                Map.copyOf(toolSkillNames));
    }

    private List<Skill> collectSkills(Conversation conversation, Agent agent) {
        Map<Long, Skill> skills = new LinkedHashMap<>();
        if (agent != null) {
            for (AgentSkill agentSkill : agentSkillRepository.findByAgentId(agent.getId())) {
                Skill skill = agentSkill.getSkill();
                if (Boolean.TRUE.equals(skill.getEnabled())) {
                    skills.put(skill.getId(), skill);
                }
            }
        }
        for (Skill skill : skillRegistry.getActiveSkillsForConversation(conversation.getId())) {
            if (Boolean.TRUE.equals(skill.getEnabled())) {
                skills.putIfAbsent(skill.getId(), skill);
            }
        }
        return new ArrayList<>(skills.values());
    }

    /** 技能触发判断：auto / keyword / manual */
    private boolean isTriggered(Skill skill, String userMessage, Set<Long> conversationActiveIds) {
        String mode = skill.getTriggerMode() != null ? skill.getTriggerMode().toLowerCase(Locale.ROOT) : "auto";
        return switch (mode) {
            case "manual" -> conversationActiveIds.contains(skill.getId());
            case "keyword" -> matchesKeywords(skill, userMessage) || conversationActiveIds.contains(skill.getId());
            default -> true;
        };
    }

    private boolean matchesKeywords(Skill skill, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return true;
        }
        String keywords = skill.getKeywords();
        if (keywords == null || keywords.isBlank()) {
            return false;
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        for (String keyword : keywords.split("[,，\\s]+")) {
            if (!keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> limitTools(Set<String> toolKeys) {
        if (toolKeys.size() <= maxTools) {
            return List.copyOf(toolKeys);
        }
        return toolKeys.stream().limit(maxTools).toList();
    }

    private Map<Long, SkillConfig> userConfigMap(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        return skillConfigRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(sc -> sc.getSkill().getId(), sc -> sc, (a, b) -> a));
    }

    private Long resolveTeamId(Conversation conversation, Agent agent) {
        Team team = conversation.getTeam();
        if (team != null) {
            return team.getId();
        }
        if (agent != null && agent.getTeam() != null) {
            return agent.getTeam().getId();
        }
        return null;
    }

    /** 向系统提示词追加当前用户可访问的数据源与接口清单 */
    private String buildAvailableResources(Long userId, Long teamId, Set<String> toolKeys) {
        if (userId == null) {
            return "";
        }
        boolean hasSql = hasToolKind(toolKeys, "sql");
        boolean hasHttp = hasToolKind(toolKeys, "http");
        StringBuilder sb = new StringBuilder();

        if (hasSql) {
            var datasources = datasourceService.list(userId, teamId);
            if (!datasources.isEmpty()) {
                sb.append("\n\n## 可访问的业务数据源\n");
                datasources.forEach(ds -> sb.append("- ").append(ds.getDsCode())
                        .append("：").append(ds.getDsName())
                        .append("（").append(ds.getDbType()).append("）\n"));
                sb.append("调用数据查询工具时，在参数 datasource 中传对应的数据源编码。\n");
            }
        }
        if (hasHttp) {
            var endpoints = apiEndpointService.list(userId, teamId);
            if (!endpoints.isEmpty()) {
                sb.append("\n## 可调用的业务接口\n");
                endpoints.forEach(ep -> sb.append("- ").append(ep.getApiCode())
                        .append("：").append(ep.getApiName())
                        .append("（").append(ep.getHttpMethod()).append(" ")
                        .append(ep.getBaseUrl()).append(ep.getPath() != null ? ep.getPath() : "")
                        .append("）\n"));
                sb.append("调用接口工具时，在参数 endpoint 中传对应的接口编码。\n");
            }
        }
        return sb.toString();
    }

    /** 技能声明的工具中是否包含匿名不可用的工具 */
    private boolean hasRestrictedTool(List<String> toolKeys) {
        for (String toolKey : toolKeys) {
            boolean restricted = extensionRuntime.findTool(toolKey)
                    .map(handle -> !Boolean.TRUE.equals(handle.tool().getAnonymousAllowed()))
                    .orElse(true);
            if (restricted) return true;
        }
        return false;
    }

    private boolean hasToolKind(Set<String> toolKeys, String kind) {
        for (String toolKey : toolKeys) {
            boolean matched = extensionRuntime.findTool(toolKey)
                    .map(handle -> kind.equalsIgnoreCase(handle.tool().getKind()))
                    .orElse(false);
            if (matched) return true;
        }
        return false;
    }

    private String appendUserInfo(String systemPrompt, String username) {
        if (username != null && !username.isBlank()) {
            return systemPrompt + "\n\n当前用户: " + username;
        }
        return systemPrompt + "\n\n## 安全限制\n"
                + "当前用户为匿名访问，只能使用标记为匿名可用的工具。\n"
                + "如果用户请求涉及数据查询、接口调用或文件导出，请礼貌告知需要登录后才能使用。";
    }
}
