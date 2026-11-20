package fun.jinwei.ai.biz.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源展示对象（密码只返回掩码）。
 */
@Data
@Builder
public class DatasourceResponse {
    private Long id;
    private String dsCode;
    private String dsName;
    private String dbType;
    private String jdbcUrl;
    private String driverClass;
    private String driverJarPath;
    private String username;
    private String passwordMasked;
    private String schemaName;
    private Boolean readOnly;
    private Integer queryMaxRows;
    private Integer queryTimeoutSec;
    private Long ownerId;
    private Long teamId;
    private String visibility;
    private String description;
    private LocalDateTime lastTestAt;
    private Boolean lastTestOk;
    private Boolean enabled;
    private long tableCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
