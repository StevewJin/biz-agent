package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 数据源表清单缓存（内省结果），用于技能提示词注入。
 */
@Entity
@Table(name = "ba_datasource_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasourceTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datasource_id", nullable = false)
    private Long datasourceId;

    @Column(name = "table_name", nullable = false, length = 200)
    private String tableName;

    @Column(name = "table_comment", length = 1000)
    private String tableComment;

    @Column(name = "row_estimate")
    private Long rowEstimate;

    @Column(name = "synced_at", nullable = false)
    @Builder.Default
    private LocalDateTime syncedAt = LocalDateTime.now();
}
