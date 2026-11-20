package fun.jinwei.ai.biz.extension;

import fun.jinwei.ai.biz.extension.api.ExtensionContext;
import fun.jinwei.ai.biz.extension.api.ExtensionException;
import fun.jinwei.ai.biz.extension.api.HttpExecutor;
import fun.jinwei.ai.biz.extension.api.SqlExecutor;
import fun.jinwei.ai.biz.service.apicatalog.ApiEndpointService;
import fun.jinwei.ai.biz.service.apicatalog.ApiExecutor;
import fun.jinwei.ai.biz.service.datasource.DatasourceService;
import fun.jinwei.ai.biz.service.tool.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 扩展包 SPI 上下文实现：把平台能力以受控方式暴露给代码类扩展包。
 */
public class ExtensionContextImpl implements ExtensionContext {

    private final ExecutionContext delegate;
    private final String extCode;
    private final DatasourceService datasourceService;
    private final ApiEndpointService apiEndpointService;
    private final ApiExecutor apiExecutor;
    private final Path workDir;

    public ExtensionContextImpl(ExecutionContext delegate, String extCode,
                                DatasourceService datasourceService,
                                ApiEndpointService apiEndpointService,
                                ApiExecutor apiExecutor,
                                Path workDir) {
        this.delegate = delegate;
        this.extCode = extCode;
        this.datasourceService = datasourceService;
        this.apiEndpointService = apiEndpointService;
        this.apiExecutor = apiExecutor;
        this.workDir = workDir;
        ensureWorkDir();
    }

    private void ensureWorkDir() {
        try {
            Files.createDirectories(workDir);
        } catch (Exception ignored) {
            // 延迟到实际使用时再报错
        }
    }

    @Override
    public Long userId() {
        return delegate.getUserId();
    }

    @Override
    public Long teamId() {
        return delegate.getTeamId();
    }

    @Override
    public Long conversationId() {
        return delegate.getConversationId();
    }

    @Override
    public boolean anonymous() {
        return delegate.isAnonymous();
    }

    @Override
    public String username() {
        return delegate.getUsername();
    }

    @Override
    public SqlExecutor sql(String datasourceCode) {
        if (datasourceCode == null || datasourceCode.isBlank()) {
            throw new ExtensionException("数据源编码不能为空");
        }
        var datasource = datasourceService
                .findVisibleByCode(datasourceCode, delegate.getUserId(), delegate.getTeamId())
                .orElseThrow(() -> new ExtensionException("数据源不存在或无权访问: " + datasourceCode));
        return datasourceService.executorFor(datasource);
    }

    @Override
    public HttpExecutor http(String endpointCode) {
        return (code, params) -> {
            var endpoint = apiEndpointService
                    .findVisibleByCode(code, delegate.getUserId(), delegate.getTeamId())
                    .orElseThrow(() -> new ExtensionException("接口不存在或无权访问: " + code));
            return apiExecutor.invoke(endpoint, params);
        };
    }

    @Override
    public Path workDir() {
        return workDir;
    }

    public String extCode() {
        return extCode;
    }

    public static Path resolveWorkDir(String storageDir, String extCode) {
        return Paths.get(storageDir, extCode, "work").toAbsolutePath().normalize();
    }
}
