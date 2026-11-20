package fun.jinwei.ai.biz.service.apicatalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.entity.ApiEndpoint;
import fun.jinwei.ai.biz.security.OutboundPolicy;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 业务系统接口调用器。
 *
 * <p>只允许调用 {@link ApiEndpoint} 中已登记的接口，禁止任意 URL 请求。
 * 鉴权信息由平台注入，扩展包与 LLM 都无法读取明文凭证。</p>
 */
@Slf4j
@Component
public class ApiExecutor {

    private static final Pattern PATH_VAR = Pattern.compile("\\{(\\w+)}");
    private static final int MAX_RESPONSE_LENGTH = 8000;

    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;
    private final OutboundPolicy outboundPolicy;
    private final WebClient webClient;

    public ApiExecutor(ObjectMapper objectMapper, EncryptionService encryptionService,
                       OutboundPolicy outboundPolicy) {
        this.objectMapper = objectMapper;
        this.encryptionService = encryptionService;
        this.outboundPolicy = outboundPolicy;
        this.webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /** 调用接口，返回响应文本（超长截断） */
    public String invoke(ApiEndpoint endpoint, Map<String, Object> params) {
        Map<String, Object> remaining = new LinkedHashMap<>(params != null ? params : Map.of());
        String path = endpoint.getPath() != null ? endpoint.getPath() : "";
        String method = endpoint.getHttpMethod() != null
                ? endpoint.getHttpMethod().toUpperCase() : "GET";

        // 1. 路径变量替换并消费参数
        Matcher matcher = PATH_VAR.matcher(path);
        StringBuilder resolvedPath = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = remaining.remove(name);
            if (value == null) {
                throw new IllegalArgumentException("缺少路径参数: " + name);
            }
            matcher.appendReplacement(resolvedPath,
                    UriUtils.encodePathSegment(String.valueOf(value), StandardCharsets.UTF_8));
        }
        matcher.appendTail(resolvedPath);

        // 2. 组装 URL
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(joinUrl(endpoint.getBaseUrl(), resolvedPath.toString()));
        boolean bodyMethod = !"GET".equals(method) && !"DELETE".equals(method) && !"HEAD".equals(method);
        Object body = null;
        if (bodyMethod) {
            body = remaining.containsKey("body") ? remaining.get("body") : remaining;
        } else {
            remaining.forEach((k, v) -> {
                if (v != null) builder.queryParam(k, v);
            });
        }
        URI uri = builder.build(true).toUri();
        // 执行期二次校验（防止已存档配置绕过创建时的校验）
        outboundPolicy.assertHttpUrlAllowed(uri.toString(), "接口地址");

        // 3. 请求头 + 鉴权
        WebClient.RequestBodySpec request = webClient.method(HttpMethod.valueOf(method))
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);
        applyHeaders(request, endpoint);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON);
            try {
                request.bodyValue(body instanceof String s ? s : objectMapper.writeValueAsString(body));
            } catch (Exception e) {
                throw new IllegalArgumentException("请求体序列化失败: " + e.getMessage());
            }
        }

        long start = System.currentTimeMillis();
        try {
            String responseBody = request.exchangeToMono(resp -> resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(text -> "HTTP " + resp.statusCode().value() + "\n" + text))
                    .timeout(Duration.ofMillis(endpoint.getTimeoutMs() != null
                            ? endpoint.getTimeoutMs() : 30000))
                    .block();
            String result = responseBody != null ? responseBody : "";
            log.info("接口调用完成: {} {} ({}ms)", method, uri, System.currentTimeMillis() - start);
            return result.length() > MAX_RESPONSE_LENGTH
                    ? result.substring(0, MAX_RESPONSE_LENGTH) + "\n...(已截断)"
                    : result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("接口调用失败: {} {} - {}", method, uri, e.getMessage());
            throw new IllegalStateException("接口调用失败: " + e.getMessage(), e);
        }
    }

    private void applyHeaders(WebClient.RequestBodySpec request, ApiEndpoint endpoint) {
        Map<String, Object> defaultHeaders = parseJsonMap(endpoint.getDefaultHeaders());
        defaultHeaders.forEach((k, v) -> {
            if (v != null) request.header(k, String.valueOf(v));
        });

        Map<String, Object> auth = parseAuthConfig(endpoint.getAuthConfigEncrypted());
        String authType = endpoint.getAuthType() != null ? endpoint.getAuthType() : "none";
        switch (authType) {
            case "bearer" -> {
                Object token = auth.get("token");
                if (token != null) request.header("Authorization", "Bearer " + token);
            }
            case "basic" -> {
                String username = String.valueOf(auth.getOrDefault("username", ""));
                String password = String.valueOf(auth.getOrDefault("password", ""));
                String encoded = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                request.header("Authorization", "Basic " + encoded);
            }
            case "api_key" -> {
                Object header = auth.getOrDefault("header", "X-API-Key");
                Object key = auth.get("key");
                if (key != null) request.header(String.valueOf(header), String.valueOf(key));
            }
            default -> {
                // none
            }
        }
    }

    private Map<String, Object> parseAuthConfig(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return Map.of();
        }
        try {
            String json = encryptionService.decrypt(encrypted);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("接口鉴权配置解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl != null ? baseUrl.trim() : "";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path == null || path.isBlank()) {
            return base;
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }
}
