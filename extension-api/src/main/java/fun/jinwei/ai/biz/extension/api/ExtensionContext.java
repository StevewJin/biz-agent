package fun.jinwei.ai.biz.extension.api;

import java.nio.file.Path;

/**
 * 扩展包工具执行上下文。仅暴露受控能力，不暴露 Spring 容器或平台内部对象。
 */
public interface ExtensionContext {

    Long userId();

    Long teamId();

    Long conversationId();

    boolean anonymous();

    String username();

    /** 获取只读 SQL 执行器；无权限或数据源不存在时抛出 {@link ExtensionException} */
    SqlExecutor sql(String datasourceCode);

    /** 获取接口调用器；接口未登记或越权时抛出 {@link ExtensionException} */
    HttpExecutor http(String endpointCode);

    /** 扩展包私有可写目录（沙箱根） */
    Path workDir();
}
