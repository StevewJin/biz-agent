package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.AgentExtension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentExtensionRepository extends JpaRepository<AgentExtension, Long> {

    List<AgentExtension> findByAgentId(Long agentId);

    Optional<AgentExtension> findByAgentIdAndExtensionId(Long agentId, Long extensionId);

    void deleteByAgentIdAndExtensionId(Long agentId, Long extensionId);
}
