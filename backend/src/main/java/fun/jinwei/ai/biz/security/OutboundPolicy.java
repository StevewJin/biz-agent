package fun.jinwei.ai.biz.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 出站地址安全策略（SSRF 防护）。
 *
 * <p>接口调用与数据源连接都可能被用来访问内网或云元数据服务，因此统一在此判定：
 * 默认拒绝回环地址、链路本地地址与云元数据地址；内网地址由配置决定（内网部署通常需放开）。</p>
 */
@Slf4j
@Component
public class OutboundPolicy {

    /** 是否允许访问内网地址（10/172.16-31/192.168/fc00:: 等） */
    @Value("${app.security.outbound.allow-private-networks:false}")
    private boolean allowPrivateNetworks;

    /** 是否允许访问回环地址（localhost/127.x/::1） */
    @Value("${app.security.outbound.allow-loopback:false}")
    private boolean allowLoopback;

    /** 主机白名单，逗号分隔；命中则直接放行 */
    @Value("${app.security.outbound.allowed-hosts:}")
    private String allowedHostsConfig;

    /** 数据源 JDBC 是否允许内网地址（内网数据库为主要场景，默认放开） */
    @Value("${app.datasource.allow-private-networks:true}")
    private boolean datasourceAllowPrivate;

    /**
     * 校验 HTTP 接口地址。
     *
     * @param url   完整地址
     * @param label 场景描述（用于错误提示）
     */
    public void assertHttpUrlAllowed(String url, String label) {
        URI uri = parse(url, label);
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(label + " 仅支持 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(label + " 缺少主机名");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (matchesAllowlist(normalized)) {
            return;
        }
        if (isMetadataOrLoopback(normalized)) {
            if (!allowLoopback) {
                throw new IllegalArgumentException(label + " 不允许访问回环/元数据地址: " + host);
            }
            return;
        }
        if (isPrivateHost(normalized) && !allowPrivateNetworks) {
            throw new IllegalArgumentException(label + " 不允许访问内网地址: " + host
                    + "（可在 app.security.outbound.allowed-hosts 中显式放行）");
        }
    }

    /**
     * 校验数据源 JDBC 地址：允许内网数据库，但禁止云元数据与回环地址。
     */
    public void assertJdbcUrlAllowed(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC 地址不能为空");
        }
        String host = extractJdbcHost(jdbcUrl);
        if (host == null) {
            return;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (matchesAllowlist(normalized)) {
            return;
        }
        if (isMetadataHost(normalized)) {
            throw new IllegalArgumentException("数据源不允许连接到云元数据地址: " + host);
        }
        if (isLoopback(normalized) && !allowLoopback) {
            throw new IllegalArgumentException("数据源不允许连接回环地址: " + host
                    + "（可在 app.security.outbound.allow-loopback 中放行）");
        }
        if (isPrivateHost(normalized) && !datasourceAllowPrivate) {
            throw new IllegalArgumentException("数据源不允许连接内网地址: " + host);
        }
    }

    private URI parse(String url, String label) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        // 路径变量 {id} 不是合法 URI 字符，校验前替换为占位符
        String sanitized = url.trim().replaceAll("\\{[^/}]*}", "x");
        try {
            return URI.create(sanitized);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " 格式不合法: " + e.getMessage());
        }
    }

    /** 从 jdbc:xxx://host:port/db 中提取主机 */
    private String extractJdbcHost(String jdbcUrl) {
        int index = jdbcUrl.indexOf("//");
        if (index < 0) {
            return null;
        }
        String rest = jdbcUrl.substring(index + 2);
        int end = rest.length();
        for (char c : new char[]{'/', '?', ';', ':'}) {
            int pos = rest.indexOf(c);
            if (pos >= 0 && pos < end) {
                end = pos;
            }
        }
        String host = rest.substring(0, end).trim();
        return host.isEmpty() ? null : host;
    }

    private boolean matchesAllowlist(String host) {
        if (allowedHostsConfig == null || allowedHostsConfig.isBlank()) {
            return false;
        }
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedHostsConfig.split(",")));
        for (String item : allowed) {
            String trimmed = item.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;
            if (trimmed.equals(host) || (trimmed.startsWith("*.") && host.endsWith(trimmed.substring(1)))) {
                return true;
            }
        }
        return false;
    }

    private boolean isMetadataOrLoopback(String host) {
        return isLoopback(host) || isMetadataHost(host);
    }

    private boolean isLoopback(String host) {
        return "localhost".equals(host) || host.startsWith("127.") || "::1".equals(host)
                || "0.0.0.0".equals(host);
    }

    private boolean isMetadataHost(String host) {
        return "169.254.169.254".equals(host) || host.startsWith("169.254.")
                || "metadata.google.internal".equals(host);
    }

    private boolean isPrivateHost(String host) {
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("fd")
                || host.startsWith("fc") || host.startsWith("fe80")) {
            return true;
        }
        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }
}
