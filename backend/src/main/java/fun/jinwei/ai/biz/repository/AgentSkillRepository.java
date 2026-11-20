package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.AgentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentSkillRepository extends JpaRepository<AgentSkill, Long> {

    @Query("SELECT ags FROM AgentSkill ags JOIN FETCH ags.skill WHERE ags.agent.id = :agentId")
    List<AgentSkill> findByAgentId(@Param("agentId") Long agentId);

    @Query("SELECT ags FROM AgentSkill ags JOIN FETCH ags.skill WHERE ags.agent.id = :agentId AND ags.skill.id = :skillId")
    Optional<AgentSkill> findByAgentIdAndSkillId(@Param("agentId") Long agentId, @Param("skillId") Long skillId);

    void deleteByAgentIdAndSkillId(Long agentId, Long skillId);
}
