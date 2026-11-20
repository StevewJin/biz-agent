package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 业务系统接口（已登记的 HTTP 端点）。
 *
 * <p>扩展包的 http 工具只能调用这里登记过的接口，避免任意 URL 请求。</p>
 */
@Entity
@Table(name = "ba_api_endpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_code", nullable = false, unique = true, length = 100)
    private String apiCode;

    @Column(name = "api_name", nullable = false, length = 200)
    private String apiName;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(nullable = false, length = 500)
    @Builder.Default
    private String path = "";

    @Column(name = "http_method", nullable = false, length = 10)
    @Builder.Default
    private String httpMethod = "GET";

    @Column(name = "auth_type", nullable = false, length = 20)
    @Builder.Default
    private String authType = "none";

    @Column(name = "auth_config_encrypted", length = 2000)
    private String authConfigEncrypted;

    @Column(name = "default_headers", length = 2000)
    private String defaultHeaders;

    @Column(name = "param_schema", length = 5000)
    private String paramSchema;

    @Column(name = "response_schema", length = 5000)
    private String responseSchema;

    @Column(name = "request_example", length = 2000)
    private String requestExample;

    @Column(name = "timeout_ms", nullable = false)
    @Builder.Default
    private Integer timeoutMs = 30000;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String visibility = "personal";

    @Column(length = 2000)
    private String description;

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
