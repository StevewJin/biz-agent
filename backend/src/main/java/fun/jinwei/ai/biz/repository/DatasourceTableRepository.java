package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.DatasourceTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DatasourceTableRepository extends JpaRepository<DatasourceTable, Long> {

    List<DatasourceTable> findByDatasourceIdOrderByTableNameAsc(Long datasourceId);

    void deleteByDatasourceId(Long datasourceId);

    long countByDatasourceId(Long datasourceId);

    /** 最近一次内省时间（用于提示词缓存失效判定） */
    @Query("SELECT MAX(t.syncedAt) FROM DatasourceTable t")
    LocalDateTime findMaxSyncedAt();

    /** 各数据源表数量（避免列表页 N+1 查询） */
    @Query("SELECT t.datasourceId, COUNT(t) FROM DatasourceTable t GROUP BY t.datasourceId")
    List<Object[]> countGroupByDatasource();
}
