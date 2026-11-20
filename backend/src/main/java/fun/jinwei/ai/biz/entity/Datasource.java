package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 业务系统数据库（数据源）。
 *
 * <p>技能包通过 ds_code 引用数据源，扩展包的 sql 工具通过 binding.datasource 引用数据源。</p>
 */
@Entity
@Table(name = "ba_datasources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Datasource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ds_code", nullable = false, unique = true, length = 100)
    private String dsCode;

    @Column(name = "ds_name", nullable = false, length = 200)
    private String dsName;

    @Column(name = "db_type", nullable = false, length = 50)
    @Builder.Default
    private String dbType = "postgresql";

    @Column(name = "jdbc_url", nullable = false, length = 1000)
    private String jdbcUrl;

    @Column(name = "driver_class", length = 200)
    private String driverClass;

    @Column(name = "driver_jar_path", length = 500)
    private String driverJarPath;

    @Column(length = 200)
    private String username;

    @Column(name = "password_encrypted", length = 1000)
    private String passwordEncrypted;

    @Column(name = "schema_name", length = 100)
    private String schemaName;

    @Column(name = "read_only", nullable = false)
    @Builder.Default
    private Boolean readOnly = Boolean.TRUE;

    @Column(name = "query_max_rows", nullable = false)
    @Builder.Default
    private Integer queryMaxRows = 2000;

    @Column(name = "query_timeout_sec", nullable = false)
    @Builder.Default
    private Integer queryTimeoutSec = 30;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String visibility = "personal";

    @Column(length = 2000)
    private String description;

    @Column(name = "last_test_at")
    private LocalDateTime lastTestAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
