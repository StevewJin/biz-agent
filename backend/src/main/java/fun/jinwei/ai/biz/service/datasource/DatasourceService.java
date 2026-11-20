package fun.jinwei.ai.biz.service.datasource;

import fun.jinwei.ai.biz.dto.request.DatasourceRequest;
import fun.jinwei.ai.biz.dto.response.DatasourceResponse;
import fun.jinwei.ai.biz.entity.Datasource;
import fun.jinwei.ai.biz.repository.DatasourceRepository;
import fun.jinwei.ai.biz.repository.DatasourceTableRepository;
import fun.jinwei.ai.biz.security.OutboundPolicy;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务系统数据库（数据源）管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceRepository datasourceRepository;
    private final DatasourceTableRepository tableRepository;
    private final DatasourceConnectionFactory connectionFactory;
    private final DatasourceIntrospectionService introspectionService;
    private final EncryptionService encryptionService;
    private final OutboundPolicy outboundPolicy;

    /** 外部驱动 jar 允许存放目录（防止任意路径加载代码） */
    @Value("${app.datasource.driver-dir:./data/drivers}")
    private String driverDir;

    public List<DatasourceResponse> list(Long userId, Long teamId) {
        // 单次分组统计，避免每条数据源一次 count 查询
        java.util.Map<Long, Long> tableCounts = new java.util.HashMap<>();
        for (Object[] row : tableRepository.countGroupByDatasource()) {
            tableCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return datasourceRepository.findAllByOrderByIdDesc().stream()
                .filter(ds -> canView(ds, userId, teamId))
                .map(ds -> toResponse(ds, tableCounts.getOrDefault(ds.getId(), 0L)))
                .sorted(Comparator.comparing(DatasourceResponse::getId).reversed())
                .toList();
    }

    public DatasourceResponse get(Long id, Long userId, Long teamId) {
        Datasource ds = require(id);
        assertAccess(ds, userId, teamId);
        return toResponse(ds);
    }

    @Transactional
    public DatasourceResponse create(DatasourceRequest req, Long userId, Long teamId, boolean admin) {
        validate(req, admin);
        Datasource ds = Datasource.builder()
                .dsCode(req.getDsCode().trim())
                .dsName(req.getDsName())
                .dbType(defaultIfBlank(req.getDbType(), "postgresql"))
                .jdbcUrl(req.getJdbcUrl())
                .driverClass(req.getDriverClass())
                .driverJarPath(req.getDriverJarPath())
                .username(req.getUsername())
                .passwordEncrypted(encrypt(req.getPassword()))
                .schemaName(req.getSchemaName())
                .readOnly(req.getReadOnly() == null || req.getReadOnly())
                .queryMaxRows(req.getQueryMaxRows() != null ? req.getQueryMaxRows() : 2000)
                .queryTimeoutSec(req.getQueryTimeoutSec() != null ? req.getQueryTimeoutSec() : 30)
                .ownerId(userId)
                .teamId(teamId)
                .visibility(defaultIfBlank(req.getVisibility(), teamId != null ? "team" : "personal"))
                .description(req.getDescription())
                .enabled(req.getEnabled() == null || req.getEnabled())
                .build();
        ds = datasourceRepository.save(ds);
        log.info("数据源已创建: {} ({})", ds.getDsName(), ds.getDsCode());
        return toResponse(ds);
    }

    @Transactional
    public DatasourceResponse update(Long id, DatasourceRequest req, Long userId, Long teamId, boolean admin) {
        Datasource ds = require(id);
        assertManage(ds, userId, admin);
        if (req.getJdbcUrl() != null && !req.getJdbcUrl().isBlank()) {
            outboundPolicy.assertJdbcUrlAllowed(req.getJdbcUrl());
        }
        if (req.getDriverJarPath() != null && !req.getDriverJarPath().isBlank()) {
            validateDriverJar(req.getDriverJarPath(), admin);
        }
        if (req.getDsCode() != null && !req.getDsCode().isBlank() && !req.getDsCode().equals(ds.getDsCode())) {
            if (datasourceRepository.findByDsCode(req.getDsCode()).isPresent()) {
                throw new IllegalArgumentException("数据源编码已存在: " + req.getDsCode());
            }
            ds.setDsCode(req.getDsCode().trim());
        }
        if (req.getDsName() != null) ds.setDsName(req.getDsName());
        if (req.getDbType() != null) ds.setDbType(req.getDbType());
        if (req.getJdbcUrl() != null) ds.setJdbcUrl(req.getJdbcUrl());
        if (req.getDriverClass() != null) ds.setDriverClass(req.getDriverClass());
        if (req.getDriverJarPath() != null) ds.setDriverJarPath(req.getDriverJarPath());
        if (req.getUsername() != null) ds.setUsername(req.getUsername());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            ds.setPasswordEncrypted(encrypt(req.getPassword()));
        }
        if (req.getSchemaName() != null) ds.setSchemaName(req.getSchemaName());
        if (req.getReadOnly() != null) ds.setReadOnly(req.getReadOnly());
        if (req.getQueryMaxRows() != null) ds.setQueryMaxRows(req.getQueryMaxRows());
        if (req.getQueryTimeoutSec() != null) ds.setQueryTimeoutSec(req.getQueryTimeoutSec());
        if (req.getVisibility() != null) ds.setVisibility(req.getVisibility());
        if (req.getDescription() != null) ds.setDescription(req.getDescription());
        if (req.getEnabled() != null) ds.setEnabled(req.getEnabled());
        ds = datasourceRepository.save(ds);
        connectionFactory.evict(id);
        log.info("数据源已更新: {}", ds.getDsCode());
        return toResponse(ds);
    }

    @Transactional
    public void delete(Long id, Long userId, Long teamId, boolean admin) {
        Datasource ds = require(id);
        assertManage(ds, userId, admin);
        connectionFactory.evict(id);
        tableRepository.deleteByDatasourceId(id);
        datasourceRepository.delete(ds);
        log.info("数据源已删除: {}", ds.getDsCode());
    }

    /** 连接测试（结果写入 last_test_*） */
    @Transactional
    public DatasourceResponse test(Long id) {
        Datasource ds = require(id);
        boolean ok;
        String message = "连接成功";
        try {
            connectionFactory.test(ds);
            ok = true;
        } catch (SQLException | RuntimeException e) {
            ok = false;
            message = e.getMessage();
            log.warn("数据源连接测试失败: {} - {}", ds.getDsCode(), message);
        }
        ds.setLastTestAt(LocalDateTime.now());
        ds.setLastTestOk(ok);
        datasourceRepository.save(ds);
        DatasourceResponse response = toResponse(ds);
        if (!ok) {
            throw new IllegalStateException("连接失败: " + message);
        }
        return response;
    }

    public int introspect(Long id) {
        return introspectionService.introspect(id);
    }

    public List<Map<String, Object>> listTables(Long id) {
        return introspectionService.listTables(id).stream()
                .map(t -> Map.<String, Object>of(
                        "tableName", t.getTableName(),
                        "comment", t.getTableComment() != null ? t.getTableComment() : "",
                        "syncedAt", t.getSyncedAt()))
                .toList();
    }

    public List<Map<String, Object>> describeTable(Long id, String tableName) {
        return introspectionService.listColumns(id, tableName).stream()
                .map(c -> Map.<String, Object>of(
                        "column", c.getColumnName(),
                        "type", c.getDataType() != null ? c.getDataType() : "",
                        "nullable", c.getNullable(),
                        "comment", c.getColumnComment() != null ? c.getColumnComment() : ""))
                .toList();
    }

    /** 预览查询（管理页面调试用） */
    public List<Map<String, Object>> previewQuery(Long id, String sql, Map<String, Object> params) {
        Datasource ds = require(id);
        return executorFor(ds).query(sql, params);
    }

    /** 构造只读执行器 */
    public ReadOnlySqlExecutor executorFor(Datasource ds) {
        return new ReadOnlySqlExecutor(
                connectionFactory.getPool(ds.getId()),
                ds.getDsCode(),
                ds.getQueryMaxRows() != null ? ds.getQueryMaxRows() : 2000,
                ds.getQueryTimeoutSec() != null ? ds.getQueryTimeoutSec() : 30);
    }

    /** 按编码查找当前用户可见的数据源 */
    public Optional<Datasource> findVisibleByCode(String dsCode, Long userId, Long teamId) {
        return datasourceRepository.findByDsCode(dsCode)
                .filter(ds -> Boolean.TRUE.equals(ds.getEnabled()))
                .filter(ds -> canView(ds, userId, teamId));
    }

    public Datasource requireByCode(String dsCode) {
        return datasourceRepository.findByDsCode(dsCode)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + dsCode));
    }

    public Datasource require(Long id) {
        return datasourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));
    }

    public boolean canView(Datasource ds, Long userId, Long teamId) {
        if (Boolean.TRUE.equals(ds.getOwnerId().equals(userId))) return true;
        if ("public".equalsIgnoreCase(ds.getVisibility())) return true;
        return teamId != null && teamId.equals(ds.getTeamId());
    }

    public void assertAccess(Datasource ds, Long userId, Long teamId) {
        if (!canView(ds, userId, teamId)) {
            throw new org.springframework.security.access.AccessDeniedException("无权访问该数据源: " + ds.getDsCode());
        }
    }

    /** 修改/删除权限：仅归属人或管理员 */
    public void assertManage(Datasource ds, Long userId, boolean admin) {
        if (!admin && (userId == null || !userId.equals(ds.getOwnerId()))) {
            throw new org.springframework.security.access.AccessDeniedException("无权修改该数据源: " + ds.getDsCode());
        }
    }

    private void validate(DatasourceRequest req, boolean admin) {
        if (req.getDsCode() == null || req.getDsCode().isBlank()) {
            throw new IllegalArgumentException("数据源编码不能为空");
        }
        if (!req.getDsCode().matches("[a-zA-Z][a-zA-Z0-9_\\-]{1,99}")) {
            throw new IllegalArgumentException("数据源编码需以字母开头，仅允许字母、数字、下划线和中划线");
        }
        if (req.getDsName() == null || req.getDsName().isBlank()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (req.getJdbcUrl() == null || req.getJdbcUrl().isBlank()) {
            throw new IllegalArgumentException("JDBC 地址不能为空");
        }
        outboundPolicy.assertJdbcUrlAllowed(req.getJdbcUrl());
        validateDriverJar(req.getDriverJarPath(), admin);
        datasourceRepository.findByDsCode(req.getDsCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("数据源编码已存在: " + req.getDsCode());
        });
    }

    /** 外部驱动 jar 仅允许管理员配置，且必须位于 driver-dir 目录内 */
    private void validateDriverJar(String jarPath, boolean admin) {
        if (jarPath == null || jarPath.isBlank()) {
            return;
        }
        if (!admin) {
            throw new org.springframework.security.access.AccessDeniedException("仅管理员可配置外部驱动 jar");
        }
        try {
            java.nio.file.Path base = java.nio.file.Paths.get(driverDir).toAbsolutePath().normalize();
            java.nio.file.Path jar = java.nio.file.Paths.get(jarPath).toAbsolutePath().normalize();
            if (!jar.startsWith(base)) {
                throw new IllegalArgumentException("驱动 jar 必须位于目录: " + base);
            }
            if (!java.nio.file.Files.isRegularFile(jar)) {
                throw new IllegalArgumentException("驱动 jar 不存在: " + jar);
            }
        } catch (java.nio.file.InvalidPathException e) {
            throw new IllegalArgumentException("驱动 jar 路径不合法");
        }
    }

    private String encrypt(String plain) {
        return plain == null || plain.isBlank() ? null : encryptionService.encrypt(plain);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private DatasourceResponse toResponse(Datasource ds) {
        return toResponse(ds, tableRepository.countByDatasourceId(ds.getId()));
    }

    private DatasourceResponse toResponse(Datasource ds, long tableCount) {
        return DatasourceResponse.builder()
                .id(ds.getId())
                .dsCode(ds.getDsCode())
                .dsName(ds.getDsName())
                .dbType(ds.getDbType())
                .jdbcUrl(ds.getJdbcUrl())
                .driverClass(ds.getDriverClass())
                .driverJarPath(ds.getDriverJarPath())
                .username(ds.getUsername())
                .passwordMasked(ds.getPasswordEncrypted() != null ? "******" : null)
                .schemaName(ds.getSchemaName())
                .readOnly(ds.getReadOnly())
                .queryMaxRows(ds.getQueryMaxRows())
                .queryTimeoutSec(ds.getQueryTimeoutSec())
                .ownerId(ds.getOwnerId())
                .teamId(ds.getTeamId())
                .visibility(ds.getVisibility())
                .description(ds.getDescription())
                .lastTestAt(ds.getLastTestAt())
                .lastTestOk(ds.getLastTestOk())
                .enabled(ds.getEnabled())
                .tableCount(tableCount)
                .createdAt(ds.getCreatedAt())
                .updatedAt(ds.getUpdatedAt())
                .build();
    }
}

