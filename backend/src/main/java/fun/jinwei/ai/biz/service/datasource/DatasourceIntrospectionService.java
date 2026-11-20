package fun.jinwei.ai.biz.service.datasource;

import fun.jinwei.ai.biz.entity.Datasource;
import fun.jinwei.ai.biz.entity.DatasourceColumn;
import fun.jinwei.ai.biz.entity.DatasourceTable;
import fun.jinwei.ai.biz.repository.DatasourceColumnRepository;
import fun.jinwei.ai.biz.repository.DatasourceRepository;
import fun.jinwei.ai.biz.repository.DatasourceTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据源表结构内省服务。
 *
 * <p>内省结果用于技能提示词注入（表清单与字段摘要），避免每次对话都访问业务库元数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceIntrospectionService {

    private final DatasourceRepository datasourceRepository;
    private final DatasourceTableRepository tableRepository;
    private final DatasourceColumnRepository columnRepository;
    private final DatasourceConnectionFactory connectionFactory;

    /** 同步表结构与字段，返回表数量 */
    @Transactional
    public int introspect(Long datasourceId) {
        Datasource ds = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + datasourceId));

        List<DatasourceTable> tables = new ArrayList<>();
        List<DatasourceColumn> columns = new ArrayList<>();
        DataSource pool = connectionFactory.getPool(datasourceId);

        try (Connection conn = pool.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String schema = resolveSchema(ds, conn);
            LocalDateTime now = LocalDateTime.now();

            try (ResultSet rs = meta.getTables(conn.getCatalog(), schema, "%",
                    new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName == null || tableName.isBlank()) continue;
                    tables.add(DatasourceTable.builder()
                            .datasourceId(datasourceId)
                            .tableName(tableName)
                            .tableComment(rs.getString("REMARKS"))
                            .syncedAt(now)
                            .build());

                    try (ResultSet colRs = meta.getColumns(conn.getCatalog(), schema, tableName, "%")) {
                        while (colRs.next()) {
                            columns.add(DatasourceColumn.builder()
                                    .datasourceId(datasourceId)
                                    .tableName(tableName)
                                    .columnName(colRs.getString("COLUMN_NAME"))
                                    .dataType(colRs.getString("TYPE_NAME"))
                                    .nullable("YES".equalsIgnoreCase(colRs.getString("IS_NULLABLE")))
                                    .columnComment(colRs.getString("REMARKS"))
                                    .ordinal(colRs.getInt("ORDINAL_POSITION"))
                                    .syncedAt(now)
                                    .build());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("表结构内省失败: " + e.getMessage(), e);
        }

        tableRepository.deleteByDatasourceId(datasourceId);
        columnRepository.deleteByDatasourceId(datasourceId);
        tableRepository.saveAll(tables);
        columnRepository.saveAll(columns);
        log.info("数据源内省完成: {} 张表, {} 个字段 (ds={})", tables.size(), columns.size(), ds.getDsCode());
        return tables.size();
    }

    private String resolveSchema(Datasource ds, Connection conn) {
        if (ds.getSchemaName() != null && !ds.getSchemaName().isBlank()) {
            return ds.getSchemaName();
        }
        try {
            return conn.getSchema();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasSnapshot(Long datasourceId) {
        return tableRepository.countByDatasourceId(datasourceId) > 0;
    }

    public List<DatasourceTable> listTables(Long datasourceId) {
        return tableRepository.findByDatasourceIdOrderByTableNameAsc(datasourceId);
    }

    public List<DatasourceColumn> listColumns(Long datasourceId, String tableName) {
        return columnRepository.findByDatasourceIdAndTableNameOrderByOrdinalAsc(datasourceId, tableName);
    }

    public List<DatasourceColumn> listAllColumns(Long datasourceId) {
        return columnRepository.findByDatasourceIdOrderByTableNameAscOrdinalAsc(datasourceId);
    }

    /** 内省快照版本号（用于提示词缓存失效判断） */
    public String snapshotVersion(Long datasourceId) {
        long count = tableRepository.countByDatasourceId(datasourceId);
        if (count == 0) return "0";
        List<DatasourceTable> tables = tableRepository.findByDatasourceIdOrderByTableNameAsc(datasourceId);
        LocalDateTime latest = tables.stream()
                .map(DatasourceTable::getSyncedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.MIN);
        return count + "@" + latest;
    }
}
