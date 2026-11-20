package fun.jinwei.ai.biz.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 业务系统接口展示对象（鉴权配置只返回掩码）。
 */
@Data
@Builder
public class ApiEndpointResponse {
    private Long id;
    private String apiCode;
    private String apiName;
    private String baseUrl;
    private String path;
    private String httpMethod;
    private String authType;
    private String authMasked;
    private Map<String, String> defaultHeaders;
    private String paramSchema;
    private String responseSchema;
    private String requestExample;
    private Integer timeoutMs;
    private Long ownerId;
    private Long teamId;
    private String visibility;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
