package fun.jinwei.ai.biz.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 出站安全策略回归：内网/回环/元数据地址默认拦截，白名单可放行。
 */
class OutboundPolicyTest {

    private OutboundPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new OutboundPolicy();
        ReflectionTestUtils.setField(policy, "allowPrivateNetworks", false);
        ReflectionTestUtils.setField(policy, "allowLoopback", false);
        ReflectionTestUtils.setField(policy, "allowedHostsConfig", "api.allowed.example.com");
        ReflectionTestUtils.setField(policy, "datasourceAllowPrivate", true);
    }

    @Test
    void rejectsLoopbackAndMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertHttpUrlAllowed("http://localhost:8080/api", "服务地址"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertHttpUrlAllowed("http://169.254.169.254/latest/meta-data", "服务地址"));
    }

    @Test
    void rejectsPrivateNetworkByDefault() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertHttpUrlAllowed("http://10.1.2.3/api", "服务地址"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertHttpUrlAllowed("http://192.168.1.10/api", "服务地址"));
    }

    @Test
    void allowsPublicAndWhitelistedHosts() {
        assertDoesNotThrow(() -> policy.assertHttpUrlAllowed("https://api.deepseek.com/models", "服务地址"));
        assertDoesNotThrow(() -> policy.assertHttpUrlAllowed("https://api.allowed.example.com/x", "服务地址"));
    }

    @Test
    void pathVariablesAreTolerated() {
        assertDoesNotThrow(() -> policy.assertHttpUrlAllowed(
                "https://api.deepseek.com/orders/{orderId}/items", "服务地址"));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertHttpUrlAllowed("file:///etc/passwd", "服务地址"));
    }

    @Test
    void datasourceAllowsPrivateButRejectsMetadata() {
        assertDoesNotThrow(() -> policy.assertJdbcUrlAllowed("jdbc:postgresql://10.0.0.8:5432/db"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertJdbcUrlAllowed("jdbc:postgresql://169.254.169.254:5432/db"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertJdbcUrlAllowed("jdbc:postgresql://127.0.0.1:5432/db"));
    }
}
