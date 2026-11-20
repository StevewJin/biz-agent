package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByOwnerId(Long ownerId);

    /** 是否由指定用户创建（避免触发懒加载代理） */
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
