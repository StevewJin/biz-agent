package fun.jinwei.ai.biz.config;

import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.repository.ExtensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 平台健康指标：扩展包加载状态。
 *
 * <p>存在加载失败的扩展包时返回 DEGRADED，便于容器编排与监控告警。</p>
 */
@Component
@RequiredArgsConstructor
public class PlatformHealthIndicator implements HealthIndicator {

    private final ExtensionRuntime extensionRuntime;
    private final ExtensionRepository extensionRepository;

    @Override
    public Health health() {
        try {
            int loadedPacks = extensionRuntime.all().size();
            int toolCount = extensionRuntime.allTools().size();
            int errorPacks = extensionRepository.findByStatus("error").size();
            Health.Builder builder = errorPacks > 0
                    ? Health.status("DEGRADED")
                    : Health.up();
            return builder
                    .withDetail("extensions.loadedPacks", loadedPacks)
                    .withDetail("extensions.toolCount", toolCount)
                    .withDetail("extensions.errorPacks", errorPacks)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
