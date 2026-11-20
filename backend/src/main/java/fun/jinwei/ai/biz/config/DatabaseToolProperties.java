package fun.jinwei.ai.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.tool.database")
public class DatabaseToolProperties {
    /** 是否允许访问自身数据库 (开发/测试: true, 生产: false) */
    private boolean allowSelfAccess = false;
}
