package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.Datasource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasourceRepository extends JpaRepository<Datasource, Long> {

    Optional<Datasource> findByDsCode(String dsCode);

    List<Datasource> findByEnabledTrue();

    List<Datasource> findByOwnerId(Long ownerId);

    List<Datasource> findByTeamId(Long teamId);

    List<Datasource> findAllByOrderByIdDesc();
}
