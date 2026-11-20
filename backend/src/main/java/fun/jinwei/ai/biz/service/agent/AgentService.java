package fun.jinwei.ai.biz.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.entity.*;
import fun.jinwei.ai.biz.repository.*;
import fun.jinwei.ai.biz.service.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final ConversationSkillRepository conversationSkillRepository;
    private final SkillConfigRepository skillConfigRepository;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Agent> listAgents(Long userId, Long teamId) {
        if (teamId != null) {
            return agentRepository.findByTeamIdAndEnabledTrue(teamId);
        }
        return agentRepository.findByOwnerIdAndEnabledTrue(userId);
    }

    public Agent getAgent(Long agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
    }

    @Transactional
    public Agent createAgent(Long userId, Long teamId, String name, String logo, String description,
                              String systemPrompt, Long providerId) {
        User user = userRepository.getReferenceById(userId);
        Agent.AgentBuilder builder = Agent.builder()
                .name(name).logo(logo).description(description)
                .systemPrompt(systemPrompt).owner(user)
                .scope(teamId != null ? "team" : "personal");

        if (teamId != null) {
            builder.team(Team.builder().id(teamId).build());
        }
        if (providerId != null) {
            builder.provider(LlmProvider.builder().id(providerId).build());
        }

        Agent agent = agentRepository.save(builder.build());
        log.info("智能体创建成功: {} (owner={})", name, userId);
        return agent;
    }

    @Transactional
    public Agent updateAgent(Long agentId, String name, String logo, String description,
                              String systemPrompt, Long providerId) {
        Agent agent = getAgent(agentId);
        if (name != null) agent.setName(name);
        if (logo != null) agent.setLogo(logo);
        if (description != null) agent.setDescription(description);
        if (systemPrompt != null) agent.setSystemPrompt(systemPrompt);
        if (providerId != null) agent.setProvider(LlmProvider.builder().id(providerId).build());
        return agentRepository.save(agent);
    }

    @Transactional
    public void deleteAgent(Long agentId) {
        agentRepository.deleteById(agentId);
        log.info("智能体已删除: {}", agentId);
    }

    @Transactional
    public AgentSkill bindSkill(Long agentId, Long skillId, Map<String, Object> configOverrides) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在"));

        AgentSkill existing = agentSkillRepository.findByAgentIdAndSkillId(agentId, skillId).orElse(null);
        if (existing != null) {
            if (configOverrides != null) existing.setConfigOverrides(toJson(configOverrides));
            return agentSkillRepository.save(existing);
        }

        AgentSkill as = AgentSkill.builder()
                .agent(Agent.builder().id(agentId).build())
                .skill(skill)
                .configOverrides(configOverrides != null ? toJson(configOverrides) : null)
                .build();
        return agentSkillRepository.save(as);
    }

    @Transactional
    public void unbindSkill(Long agentId, Long skillId) {
        agentSkillRepository.deleteByAgentIdAndSkillId(agentId, skillId);
    }

    public List<AgentSkill> getAgentSkills(Long agentId) {
        return agentSkillRepository.findByAgentId(agentId);
    }

    /** 获取智能体的活跃技能列表 (用于构建系统提示词) */
    public List<Skill> getActiveSkillsForAgent(Long agentId) {
        return agentSkillRepository.findByAgentId(agentId).stream()
                .map(AgentSkill::getSkill)
                .toList();
    }

    /** 获取智能体的系统提示词 (含技能注入和配置) */
    public String buildAgentPrompt(Long agentId) {
        Agent agent = getAgent(agentId);
        Long userId = agent.getOwner().getId();

        Map<Long, SkillConfig> configMap = skillConfigRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        sc -> sc.getSkill().getId(), sc -> sc, (a, b) -> a));

        StringBuilder sb = new StringBuilder();
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            sb.append(agent.getSystemPrompt());
        }

        List<AgentSkill> agentSkills = agentSkillRepository.findByAgentId(agentId);
        if (!agentSkills.isEmpty()) {
            sb.append("\n\n## 已激活的业务技能\n\n");
            for (AgentSkill as : agentSkills) {
                Skill skill = as.getSkill();
                sb.append("### ").append(skill.getDisplayName()).append("\n");
                sb.append(skill.getPromptMd() != null ? skill.getPromptMd() : "").append("\n\n");

                SkillConfig userConfig = configMap.get(skill.getId());
                String configSection = skillRegistry.buildSkillConfigPrompt(
                        skill, userConfig, as.getConfigOverrides());
                if (!configSection.isEmpty()) {
                    sb.append(configSection).append("\n\n");
                }
            }
        }
        return sb.toString();
    }

    @Transactional
    public String generateShareToken(Long agentId) {
        Agent agent = getAgent(agentId);
        String token = UUID.randomUUID().toString();
        agent.setShareToken(token);
        agentRepository.save(agent);
        return token;
    }

    @Transactional
    public String generateAesKey(Long agentId) {
        Agent agent = getAgent(agentId);
        // delegate to EncryptionService injection in controller
        return null; // actual key generation done in controller
    }

    @Transactional
    public void setAesKey(Long agentId, String aesKey) {
        Agent agent = getAgent(agentId);
        agent.setAesKey(aesKey);
        agentRepository.save(agent);
    }

    public Agent findByShareToken(String shareToken) {
        return agentRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在或已下线"));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
