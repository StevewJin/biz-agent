package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByName(String name);

    List<Skill> findByEnabledTrue();

    List<Skill> findByCategory(String category);
}
