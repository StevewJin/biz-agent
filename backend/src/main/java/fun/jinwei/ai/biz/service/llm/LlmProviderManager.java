package fun.jinwei.ai.biz.service.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.entity.LlmProvider;
import fun.jinwei.ai.biz.repository.LlmProviderRepository;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 供应商管理器 - 热切换，从 DB 加载供应商
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProviderManager {

    private final LlmProviderRepository providerRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @Value("${app.llm.deepseek.api-key:}")
    private String ymlApiKey;

    @Value("${app.llm.deepseek.base-url:https://api.deepseek.com}")
    private String ymlBaseUrl;

    @Value("${app.llm.deepseek.model:deepseek-flash}")
    private String ymlModel;

    private final Map<Long, LlmGateway> gatewayCache = new ConcurrentHashMap<>();
    private volatile LlmGateway defaultGateway;
    private volatile Long defaultProviderId;

    @PostConstruct
    public void init() {
        List<LlmProvider> providers = providerRepository.findByEnabledTrue();
        if (providers.isEmpty()) {
            log.info("DB 无 LLM 供应商，使用 yml 默认 DeepSeek 配置作为 fallback");
            return;
        }
        refreshGateways(providers);
    }

    private void refreshGateways(List<LlmProvider> providers) {
        gatewayCache.clear();
        for (LlmProvider lp : providers) {
            try {
                String apiKey = encryptionService.decrypt(lp.getApiKeyEncrypted());
                String model = extractFirstModel(lp.getModels());
                LlmGateway gateway = createGateway(lp.getProviderType(), apiKey, lp.getBaseUrl(), model);
                gatewayCache.put(lp.getId(), gateway);
                if (lp.getIsDefault()) {
                    defaultGateway = gateway;
                    defaultProviderId = lp.getId();
                }
                log.info("LLM 供应商已加载: {} (type={}, id={})", lp.getName(), lp.getProviderType(), lp.getId());
            } catch (Exception e) {
                log.error("加载 LLM 供应商失败: {} (id={})", lp.getName(), lp.getId(), e);
            }
        }
        if (defaultGateway == null && !gatewayCache.isEmpty()) {
            Map.Entry<Long, LlmGateway> first = gatewayCache.entrySet().iterator().next();
            defaultGateway = first.getValue();
            defaultProviderId = first.getKey();
        }
        log.info("LlmProviderManager 初始化完成: {} 个供应商, 默认: id={}", gatewayCache.size(), defaultProviderId);
    }

    /** 热切换 - 重新加载所有供应商 */
    public void refresh() {
        List<LlmProvider> providers = providerRepository.findByEnabledTrue();
        refreshGateways(providers);
    }

    /** 获取指定供应商 */
    public LlmGateway getGateway(Long providerId) {
        if (providerId != null && gatewayCache.containsKey(providerId)) {
            return gatewayCache.get(providerId);
        }
        return getDefaultGateway();
    }

    /** 获取默认供应商 */
    public LlmGateway getDefaultGateway() {
        return defaultGateway;
    }

    public Long getDefaultProviderId() {
        return defaultProviderId;
    }

    public Set<Long> getProviderIds() {
        return gatewayCache.keySet();
    }

    private LlmGateway createGateway(String providerType, String apiKey, String baseUrl, String model) {
        if ("deepseek".equals(providerType)) {
            return new DeepSeekProvider(apiKey, baseUrl, model, null);
        }
        // OpenAI provider - same API format as DeepSeek
        if ("openai".equals(providerType)) {
            return new DeepSeekProvider(apiKey, baseUrl, model, null);
        }
        // Default: treat as OpenAI-compatible
        log.warn("未知供应商类型 {}，使用 OpenAI 兼容模式", providerType);
        return new DeepSeekProvider(apiKey, baseUrl, model, null);
    }

    private String extractFirstModel(String modelsJson) {
        try {
            List<String> models = objectMapper.readValue(modelsJson, new TypeReference<List<String>>() {});
            return models != null && !models.isEmpty() ? models.get(0) : ymlModel;
        } catch (Exception e) {
            return ymlModel;
        }
    }
}
