package fun.jinwei.ai.biz.service.datasource;

import fun.jinwei.ai.biz.extension.api.ExtensionException;
import fun.jinwei.ai.biz.extension.api.SqlExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读 SQL 执行器。
 *
 * <p>强制约束：仅允许 SELECT / WITH 单条语句，命名参数通过 PreparedStatement 绑定，
 * 返回行数上限与查询超时由数据源配置控制。</p>
 */
@Slf4j
public class ReadOnlySqlExecutor implements SqlExecutor {

    private static final Pattern NAMED_PARAM = Pattern.compile("#\\{(\\w+)\\}|(?<![:#\\w]):(\\w+)");
    private static final Pattern MULTI_STATEMENT = Pattern.compile(";\\s*\\S");
    private static final List<String> ALLOWED_PREFIX = List.of("select", "with", "show", "explain", "describe");

    private final DataSource dataSource;
    private final String datasourceCode;
    private final int maxRows;
    private final int timeoutSeconds;

    public ReadOnlySqlExecutor(DataSource dataSource, String datasourceCode, int maxRows, int timeoutSeconds) {
        this.dataSource = dataSource;
        this.datasourceCode = datasourceCode;
        this.maxRows = maxRows;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
        if (sql == null || sql.isBlank()) {
            throw new ExtensionException("SQL 不能为空");
        }
        String normalized = sql.trim();
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_PREFIX.stream().anyMatch(lower::startsWith);
        if (!allowed) {
            throw new ExtensionException("仅允许只读查询（SELECT / WITH）");
        }
        if (MULTI_STATEMENT.matcher(normalized).find()) {
            throw new ExtensionException("禁止一次执行多条 SQL 语句");
        }

        List<Object> orderedParams = new ArrayList<>();
        String preparedSql = bindNamedParams(normalized, params, orderedParams);

        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);
            try (PreparedStatement ps = conn.prepareStatement(preparedSql)) {
                ps.setMaxRows(Math.max(1, maxRows));
                if (timeoutSeconds > 0) {
                    ps.setQueryTimeout(timeoutSeconds);
                }
                for (int i = 0; i < orderedParams.size(); i++) {
                    ps.setObject(i + 1, orderedParams.get(i));
                }
                long start = System.currentTimeMillis();
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> rows = toRows(rs);
                    log.info("数据源查询完成: ds={} rows={} ({}ms)",
                            datasourceCode, rows.size(), System.currentTimeMillis() - start);
                    return rows;
                }
            }
        } catch (SQLException e) {
            log.warn("数据源查询失败: ds={} error={}", datasourceCode, e.getMessage());
            throw new ExtensionException("查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int maxRows() {
        return maxRows;
    }

    @Override
    public String datasourceCode() {
        return datasourceCode;
    }

    /** 将 {@code #{name}} / {@code :name} 替换为问号占位符并按顺序收集参数 */
    public static String bindNamedParams(String sql, Map<String, Object> params,
                                         List<Object> orderedParams) {
        Matcher matcher = NAMED_PARAM.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            Object value = params != null ? params.get(name) : null;
            matcher.appendReplacement(sb, "?");
            orderedParams.add(value);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<Map<String, Object>> toRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String label = meta.getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(label != null && !label.isBlank() ? label : meta.getColumnName(i), value);
            }
            rows.add(row);
        }
        return rows;
    }
}
