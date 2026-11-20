package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LlmProviderRepository extends JpaRepository<LlmProvider, Long> {

    List<LlmProvider> findByEnabledTrue();

    Optional<LlmProvider> findByIsDefaultTrueAndEnabledTrue();

    List<LlmProvider> findByOwnerId(Long ownerId);

    List<LlmProvider> findByTeamId(Long teamId);
}
