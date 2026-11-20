package fun.jinwei.ai.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 扩展包运行时配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.extension")
public class ExtensionProperties {

    /** 扩展包安装目录 */
    private String storageDir = "./data/extensions";

    /** 是否允许导入含代码的扩展包（jar / mixed） */
    private boolean allowCodePacks = true;

    /** 单个扩展包最大体积（MB） */
    private int maxPackageMb = 50;

    /** 工具执行超时（秒） */
    private int toolTimeoutSeconds = 60;

    /** 内置扩展包 classpath 位置 */
    private String builtinLocation = "classpath*:extensions/builtin";

    /** 是否在启动时自动导入内置扩展包 */
    private boolean bootstrapBuiltin = true;
}
