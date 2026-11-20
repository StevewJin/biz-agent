package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 数据源字段清单缓存（内省结果）。
 */
@Entity
@Table(name = "ba_datasource_columns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasourceColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datasource_id", nullable = false)
    private Long datasourceId;

    @Column(name = "table_name", nullable = false, length = 200)
    private String tableName;

    @Column(name = "column_name", nullable = false, length = 200)
    private String columnName;

    @Column(name = "data_type", length = 100)
    private String dataType;

    private Boolean nullable;

    @Column(name = "column_comment", length = 1000)
    private String columnComment;

    private Integer ordinal;

    @Column(name = "synced_at", nullable = false)
    @Builder.Default
    private LocalDateTime syncedAt = LocalDateTime.now();
}
