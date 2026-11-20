package fun.jinwei.ai.biz.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * 业务系统接口创建/更新请求。
 */
@Data
public class ApiEndpointRequest {
    private String apiCode;
    private String apiName;
    private String baseUrl;
    private String path;
    private String httpMethod;
    private String authType;
    /** 鉴权配置：bearer -> {token}; basic -> {username,password}; api_key -> {header,key} */
    private Map<String, Object> authConfig;
    private Map<String, String> defaultHeaders;
    private String paramSchema;
    private String responseSchema;
    private String requestExample;
    private Integer timeoutMs;
    private String visibility;
    private String description;
    private Boolean enabled;
}
