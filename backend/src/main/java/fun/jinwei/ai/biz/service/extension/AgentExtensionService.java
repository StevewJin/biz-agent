package fun.jinwei.ai.biz.service.extension;

import fun.jinwei.ai.biz.entity.AgentExtension;
import fun.jinwei.ai.biz.entity.Extension;
import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.repository.AgentExtensionRepository;
import fun.jinwei.ai.biz.repository.ExtensionRepository;
import fun.jinwei.ai.biz.security.ResourceVisibility;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体与扩展包绑定服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExtensionService {

    private final AgentExtensionRepository agentExtensionRepository;
    private final ExtensionRepository extensionRepository;
    private final ExtensionRuntime extensionRuntime;
    private final ResourceVisibility visibility;

    public List<Map<String, Object>> listBindings(Long agentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentExtension binding : agentExtensionRepository.findByAgentId(agentId)) {
            Extension extension = extensionRepository.findById(binding.getExtensionId()).orElse(null);
            if (extension == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", binding.getId());
            item.put("agentId", agentId);
            item.put("extensionId", extension.getId());
            item.put("extCode", extension.getExtCode());
            item.put("extName", extension.getExtName());
            item.put("version", extension.getVersion());
            item.put("status", extension.getStatus());
            item.put("enabled", binding.getEnabled());
            result.add(item);
        }
        return result;
    }

    @Transactional
    public AgentExtension bind(Long agentId, String extCode, Long extensionId, Long userId, boolean admin) {
        Extension extension = extensionId != null
                ? extensionRepository.findById(extensionId)
                        .orElseThrow(() -> new IllegalArgumentException("扩展包不存在: " + extensionId))
                : extensionRepository.findByExtCode(extCode)
                        .orElseThrow(() -> new IllegalArgumentException("扩展包不存在: " + extCode));

        // 只能绑定当前用户可见的扩展包（个人 / 团队 / 已审核公开）
        if (!visibility.isVisible(extension.getOwnerId(), extension.getTeamId(),
                extension.getVisibility(), extension.getPublishStatus(), userId, admin)) {
            throw new AccessDeniedException("无权使用该扩展包: " + extension.getExtCode());
        }

        AgentExtension existing = agentExtensionRepository
                .findByAgentIdAndExtensionId(agentId, extension.getId()).orElse(null);
        if (existing != null) {
            existing.setEnabled(true);
            return agentExtensionRepository.save(existing);
        }
        AgentExtension binding = AgentExtension.builder()
                .agentId(agentId)
                .extensionId(extension.getId())
                .enabled(true)
                .build();
        log.info("智能体绑定扩展包: agentId={}, extCode={}", agentId, extension.getExtCode());
        return agentExtensionRepository.save(binding);
    }

    @Transactional
    public void unbind(Long agentId, String extCode) {
        Extension extension = extensionRepository.findByExtCode(extCode)
                .orElseThrow(() -> new IllegalArgumentException("扩展包不存在: " + extCode));
        agentExtensionRepository.deleteByAgentIdAndExtensionId(agentId, extension.getId());
        log.info("智能体解绑扩展包: agentId={}, extCode={}", agentId, extCode);
    }

    /** 智能体绑定的扩展包编码 */
    public List<String> boundExtensionCodes(Long agentId) {
        List<String> codes = new ArrayList<>();
        for (AgentExtension binding : agentExtensionRepository.findByAgentId(agentId)) {
            if (!Boolean.TRUE.equals(binding.getEnabled())) continue;
            extensionRuntime.all().stream()
                    .filter(loaded -> loaded.entity().getId().equals(binding.getExtensionId()))
                    .findFirst()
                    .ifPresent(loaded -> codes.add(loaded.entity().getExtCode()));
        }
        return codes;
    }
}
