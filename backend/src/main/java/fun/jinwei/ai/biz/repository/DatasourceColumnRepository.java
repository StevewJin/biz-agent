package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.DatasourceColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasourceColumnRepository extends JpaRepository<DatasourceColumn, Long> {

    List<DatasourceColumn> findByDatasourceIdOrderByTableNameAscOrdinalAsc(Long datasourceId);

    List<DatasourceColumn> findByDatasourceIdAndTableNameOrderByOrdinalAsc(Long datasourceId, String tableName);

    void deleteByDatasourceId(Long datasourceId);
}
