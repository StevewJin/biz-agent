package fun.jinwei.ai.biz.extension;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时导入内置扩展包并加载扩展包运行时。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtensionBootstrapRunner implements ApplicationRunner {

    private final ExtensionInstaller installer;
    private final ExtensionRuntime runtime;

    @Override
    public void run(ApplicationArguments args) {
        try {
            installer.bootstrapBuiltin();
        } catch (Exception e) {
            log.error("内置扩展包导入失败: {}", e.getMessage(), e);
        }
        runtime.reloadAll();
    }
}
