package fun.jinwei.ai.biz.service.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.entity.ConversationSkill;
import fun.jinwei.ai.biz.entity.Skill;
import fun.jinwei.ai.biz.entity.SkillConfig;
import fun.jinwei.ai.biz.repository.ConversationSkillRepository;
import fun.jinwei.ai.biz.repository.SkillConfigRepository;
import fun.jinwei.ai.biz.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 技能包注册表 - 管理数据库中的技能包与对话级激活状态。
 *
 * <p>技能包不再由代码定义（已移除 BizSkill），全部以数据库记录 + 技能包导入的形式管理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRegistry {

    private final SkillRepository skillRepository;
    private final ConversationSkillRepository conversationSkillRepository;
    private final SkillConfigRepository skillConfigRepository;
    private final ObjectMapper objectMapper;

    /** 获取所有启用技能 */
    public List<Skill> getAllDbSkills() {
        return skillRepository.findByEnabledTrue();
    }

    public Optional<Skill> getDbSkill(Long skillId) {
        return skillRepository.findById(skillId);
    }

    public Optional<Skill> getDbSkillByName(String name) {
        return skillRepository.findByName(name);
    }

    /** 对话的活跃技能 */
    public List<Skill> getActiveSkillsForConversation(Long conversationId) {
        return conversationSkillRepository.findByConversationIdAndEnabledTrue(conversationId).stream()
                .map(ConversationSkill::getSkill)
                .collect(Collectors.toList());
    }

    /** 激活对话技能 */
    public ConversationSkill activateSkill(Long conversationId, Long skillId, Map<String, Object> configOverrides) {
        ConversationSkill existing = conversationSkillRepository
                .findByConversationIdAndSkillId(conversationId, skillId).orElse(null);
        if (existing != null) {
            existing.setEnabled(true);
            if (configOverrides != null) {
                existing.setConfigOverrides(toJson(configOverrides));
            }
            return conversationSkillRepository.save(existing);
        }
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));
        ConversationSkill cs = ConversationSkill.builder()
                .conversation(fun.jinwei.ai.biz.entity.Conversation.builder().id(conversationId).build())
                .skill(skill)
                .enabled(true)
                .configOverrides(configOverrides != null ? toJson(configOverrides) : null)
                .build();
        return conversationSkillRepository.save(cs);
    }

    /** 停用对话技能 */
    public void deactivateSkill(Long conversationId, Long skillId) {
        conversationSkillRepository.findByConversationIdAndSkillId(conversationId, skillId)
                .ifPresent(cs -> {
                    cs.setEnabled(false);
                    conversationSkillRepository.save(cs);
                });
    }

    public Optional<SkillConfig> getUserSkillConfig(Long skillId, Long userId) {
        return skillConfigRepository.findBySkillIdAndUserId(skillId, userId);
    }

    public SkillConfig saveUserSkillConfig(Long skillId, Long userId, Map<String, Object> config) {
        SkillConfig existing = skillConfigRepository.findBySkillIdAndUserId(skillId, userId).orElse(null);
        if (existing != null) {
            existing.setConfig(toJson(config));
            existing.setUpdatedAt(java.time.LocalDateTime.now());
            return skillConfigRepository.save(existing);
        }
        SkillConfig sc = SkillConfig.builder()
                .skill(Skill.builder().id(skillId).build())
                .user(fun.jinwei.ai.biz.entity.User.builder().id(userId).build())
                .config(toJson(config))
                .build();
        return skillConfigRepository.save(sc);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 构建技能配置提示文本（configSchema + 用户配置值 + 覆盖项）。
     */
    public String buildSkillConfigPrompt(Skill skill, SkillConfig userConfig, String configOverridesJson) {
        Map<String, Object> schema = parseJsonToMap(skill.getConfigSchema());
        if (schema == null || !schema.containsKey("properties")) return "";

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        if (props == null || props.isEmpty()) return "";

        Map<String, Object> config = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldSchema = (entry.getValue() instanceof Map)
                    ? (Map<String, Object>) entry.getValue() : null;
            if (fieldSchema != null && fieldSchema.containsKey("default")) {
                config.put(entry.getKey(), fieldSchema.get("default"));
            }
        }
        if (userConfig != null) {
            Map<String, Object> base = parseJsonToMap(userConfig.getConfig());
            if (base != null) config.putAll(base);
        }
        Map<String, Object> overrides = parseJsonToMap(configOverridesJson);
        if (overrides != null) config.putAll(overrides);

        StringBuilder sb = new StringBuilder();
        sb.append("**当前配置:**\n");
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldSchema = (entry.getValue() instanceof Map)
                    ? (Map<String, Object>) entry.getValue() : null;
            if (fieldSchema == null) continue;
            String title = fieldSchema.getOrDefault("title", key).toString();
            String displayValue = config.containsKey(key) ? String.valueOf(config.get(key)) : "(未配置)";
            sb.append("- ").append(title).append(" (").append(key).append("): ").append(displayValue).append("\n");
        }
        sb.append("\n请在调用相关工具时使用以上配置信息。");
        return sb.toString();
    }

    /** 解析技能声明的工具白名单（resources.tools） */
    public List<String> getDeclaredToolKeys(Skill skill) {
        return SkillResources.parse(skill.getResourcesJson(), objectMapper).tools();
    }
}
