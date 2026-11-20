package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.SkillConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillConfigRepository extends JpaRepository<SkillConfig, Long> {

    Optional<SkillConfig> findBySkillIdAndUserId(Long skillId, Long userId);

    List<SkillConfig> findByUserId(Long userId);
}
