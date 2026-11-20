package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {

    List<Agent> findByOwnerIdAndEnabledTrue(Long ownerId);

    List<Agent> findByTeamIdAndEnabledTrue(Long teamId);

    List<Agent> findByOwnerIdAndScopeAndEnabledTrue(Long ownerId, String scope);

    Optional<Agent> findByShareToken(String shareToken);
}
