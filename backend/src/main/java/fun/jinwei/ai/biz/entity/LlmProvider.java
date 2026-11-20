package fun.jinwei.ai.biz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ba_llm_providers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LlmProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String name;

    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType;

    /** 密钥密文，禁止对外序列化 */
    @JsonIgnore
    @Column(name = "api_key_encrypted", nullable = false, length = 500)
    private String apiKeyEncrypted;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @JsonIgnore
    @Column(length = 2000, nullable = false)
    private String models;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonProperty("models")
    public List<String> getModelsList() {
        try {
            return objectMapper.readValue(models, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    @JsonProperty("models")
    public void setModelsList(List<String> list) {
        try {
            this.models = objectMapper.writeValueAsString(list != null ? list : List.of());
        } catch (Exception e) {
            this.models = "[]";
        }
    }
}
