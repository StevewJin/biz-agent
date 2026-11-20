package fun.jinwei.ai.biz.dto.request;

import lombok.Data;

/**
 * 业务系统数据库（数据源）创建/更新请求。
 *
 * <p>password 为空时表示不修改原密码。</p>
 */
@Data
public class DatasourceRequest {
    private String dsCode;
    private String dsName;
    private String dbType;
    private String jdbcUrl;
    private String driverClass;
    private String driverJarPath;
    private String username;
    private String password;
    private String schemaName;
    private Boolean readOnly;
    private Integer queryMaxRows;
    private Integer queryTimeoutSec;
    private String visibility;
    private String description;
    private Boolean enabled;
}
