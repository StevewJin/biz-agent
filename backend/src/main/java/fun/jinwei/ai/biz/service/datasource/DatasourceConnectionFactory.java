package fun.jinwei.ai.biz.service.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fun.jinwei.ai.biz.entity.Datasource;
import fun.jinwei.ai.biz.repository.DatasourceRepository;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 数据源连接池工厂。
 *
 * <p>按数据源 ID 缓存 Hikari 连接池；支持通过外部 jar 加载 JDBC 驱动（如国产数据库驱动）。
 * 所有连接默认只读，避免扩展包或技能通过查询修改业务数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceConnectionFactory {

    private final DatasourceRepository datasourceRepository;
    private final EncryptionService encryptionService;

    @Value("${app.datasource.pool-max-size:5}")
    private int poolMaxSize;

    private final Map<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();
    private final Map<String, Driver> externalDriverCache = new ConcurrentHashMap<>();

    /** 获取（或创建）数据源连接池 */
    public DataSource getPool(Long datasourceId) {
        HikariDataSource cached = poolCache.get(datasourceId);
        if (cached != null && !cached.isClosed()) {
            return cached;
        }
        Datasource ds = datasourceRepository.findById(datasourceId)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + datasourceId));
        synchronized (this) {
            HikariDataSource existing = poolCache.get(datasourceId);
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            HikariDataSource created = create(ds);
            poolCache.put(datasourceId, created);
            return created;
        }
    }

    /** 测试连接（不进入缓存） */
    public void test(Datasource ds) throws SQLException {
        try (HikariDataSource temp = create(ds); Connection conn = temp.getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("连接校验失败");
            }
        }
    }

    /** 主动失效缓存（数据源配置变更或删除时调用） */
    public void evict(Long datasourceId) {
        HikariDataSource pool = poolCache.remove(datasourceId);
        if (pool != null) {
            pool.close();
        }
    }

    public void evictAll() {
        poolCache.values().forEach(HikariDataSource::close);
        poolCache.clear();
    }

    private HikariDataSource create(Datasource ds) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("bizagent-ds-" + ds.getDsCode());
        config.setMaximumPoolSize(poolMaxSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(15000);
        config.setReadOnly(Boolean.TRUE.equals(ds.getReadOnly()));
        config.setAutoCommit(true);
        config.setJdbcUrl(ds.getJdbcUrl());
        applySchema(config, ds);

        String password = decrypt(ds.getPasswordEncrypted());
        String driverClass = ds.getDriverClass();

        if (ds.getDriverJarPath() != null && !ds.getDriverJarPath().isBlank()) {
            Driver driver = loadExternalDriver(driverClass, ds.getDriverJarPath());
            config.setDataSource(new DriverBackedDataSource(driver, ds.getJdbcUrl(),
                    buildProps(ds.getUsername(), password)));
        } else {
            if (driverClass != null && !driverClass.isBlank()) {
                config.setDriverClassName(driverClass);
            }
            config.setUsername(ds.getUsername());
            config.setPassword(password);
        }
        log.info("创建数据源连接池: {} ({})", ds.getDsCode(), ds.getJdbcUrl());
        return new HikariDataSource(config);
    }

    /**
     * PostgreSQL 系列数据库：连接建立后设置 search_path，
     * 否则未带 schema 前缀的表名（如 ba_users）会去 public 下找而报错。
     */
    private void applySchema(HikariConfig config, Datasource ds) {
        String schema = ds.getSchemaName();
        String dbType = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "";
        boolean postgresLike = dbType.contains("postgres") || dbType.contains("panwei")
                || (ds.getJdbcUrl() != null && ds.getJdbcUrl().startsWith("jdbc:postgresql:"))
                || (ds.getJdbcUrl() != null && ds.getJdbcUrl().startsWith("jdbc:panweidb:"));
        if (!postgresLike || schema == null || schema.isBlank()) {
            return;
        }
        // 仅允许安全标识符，避免拼接注入
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            log.warn("数据源 {} 的 schema 名不合法，已忽略 search_path 设置: {}", ds.getDsCode(), schema);
            return;
        }
        config.setConnectionInitSql("SET search_path TO " + schema);
    }

    private Properties buildProps(String username, String password) {
        Properties props = new Properties();
        if (username != null) props.setProperty("user", username);
        if (password != null) props.setProperty("password", password);
        return props;
    }

    private String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            return encryptionService.decrypt(ciphertext);
        } catch (Exception e) {
            log.error("数据源密码解密失败，请重新录入数据源密码: {}", e.getMessage());
            throw new IllegalStateException("数据源密码解密失败，请重新录入密码");
        }
    }

    private Driver loadExternalDriver(String driverClass, String jarPath) {
        String cacheKey = driverClass + "@" + jarPath;
        Driver cached = externalDriverCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            File jarFile = new File(jarPath);
            if (!jarFile.exists() || !jarFile.isFile()) {
                throw new IllegalArgumentException("JDBC 驱动 jar 不存在: " + jarPath);
            }
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()}, Driver.class.getClassLoader());
            Class<?> cls = loader.loadClass(driverClass);
            Driver driver = (Driver) cls.getDeclaredConstructor().newInstance();
            externalDriverCache.put(cacheKey, driver);
            log.info("已从外部 jar 加载 JDBC 驱动: {} (jar={})", driverClass, jarPath);
            return driver;
        } catch (Exception e) {
            throw new IllegalStateException("加载 JDBC 驱动失败: " + driverClass + ", " + e.getMessage(), e);
        }
    }

    /** 基于已实例化 Driver 的 DataSource 适配器（用于外部 jar 驱动） */
    static final class DriverBackedDataSource implements DataSource {
        private final Driver driver;
        private final String url;
        private final Properties props;
        private PrintWriter logWriter;
        private int loginTimeout;

        DriverBackedDataSource(Driver driver, String url, Properties props) {
            this.driver = driver;
            this.url = url;
            this.props = props;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return driver.connect(url, props);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties copy = new Properties();
            copy.putAll(props);
            if (username != null) copy.setProperty("user", username);
            if (password != null) copy.setProperty("password", password);
            return driver.connect(url, copy);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("不支持 unwrap: " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
