package fun.jinwei.ai.biz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.entity.LlmProvider;
import fun.jinwei.ai.biz.repository.LlmProviderRepository;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import fun.jinwei.ai.biz.service.llm.LlmProviderManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class LlmProviderController {

    private final LlmProviderRepository providerRepository;
    private final LlmProviderManager providerManager;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<List<LlmProvider>> listProviders(Authentication auth) {
        AccessGuard.requireAdmin(auth);
        return ApiResponse.success(providerRepository.findByEnabledTrue());
    }

    @PostMapping
    public ApiResponse<LlmProvider> createProvider(@RequestBody Map<String, Object> req, Authentication auth) {
        AccessGuard.requireAdmin(auth);
        LlmProvider lp = LlmProvider.builder()
                .name((String) req.get("name"))
                .providerType((String) req.get("providerType"))
                .apiKeyEncrypted(encryptionService.encrypt((String) req.get("apiKey")))
                .baseUrl((String) req.get("baseUrl"))
                .models(toJson(req.get("models")))
                .enabled(true)
                .build();
        lp = providerRepository.save(lp);
        providerManager.refresh();
        return ApiResponse.success(lp);
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmProvider> updateProvider(@PathVariable Long id, @RequestBody Map<String, Object> req,
                                                   Authentication auth) {
        AccessGuard.requireAdmin(auth);
        LlmProvider lp = providerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        if (req.containsKey("name")) lp.setName((String) req.get("name"));
        if (req.containsKey("apiKey")) lp.setApiKeyEncrypted(encryptionService.encrypt((String) req.get("apiKey")));
        if (req.containsKey("baseUrl")) lp.setBaseUrl((String) req.get("baseUrl"));
        if (req.containsKey("models")) lp.setModels(toJson(req.get("models")));
        if (req.containsKey("enabled")) lp.setEnabled((Boolean) req.get("enabled"));
        lp = providerRepository.save(lp);
        providerManager.refresh();
        return ApiResponse.success(lp);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id, Authentication auth) {
        AccessGuard.requireAdmin(auth);
        providerRepository.deleteById(id);
        providerManager.refresh();
        return ApiResponse.success();
    }

    @PostMapping("/{id}/set-default")
    public ApiResponse<LlmProvider> setDefault(@PathVariable Long id, Authentication auth) {
        AccessGuard.requireAdmin(auth);
        providerRepository.findByIsDefaultTrueAndEnabledTrue().ifPresent(p -> {
            p.setIsDefault(false);
            providerRepository.save(p);
        });
        LlmProvider lp = providerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        lp.setIsDefault(true);
        lp = providerRepository.save(lp);
        providerManager.refresh();
        return ApiResponse.success(lp);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
