package fun.jinwei.ai.biz.extension.api;

/**
 * 只读 SQL 执行器。平台负责连接管理、只读校验、参数绑定、行数与超时限制。
 */
public interface SqlExecutor {

    /**
     * 执行只读查询。
     *
     * @param sql    含 {@code #{name}} 或 {@code :name} 命名占位符的 SQL（禁止拼接参数）
     * @param params 命名参数
     * @return 行数据，键为列名
     */
    java.util.List<java.util.Map<String, Object>> query(String sql, java.util.Map<String, Object> params);

    /** 当前数据源允许的最大返回行数 */
    int maxRows();

    /** 数据源编码 */
    String datasourceCode();
}
