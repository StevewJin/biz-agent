package fun.jinwei.ai.biz.extension.api;

/**
 * 扩展包执行期异常（参数错误、越权、数据源不可用等）。
 */
public class ExtensionException extends RuntimeException {

    public ExtensionException(String message) {
        super(message);
    }

    public ExtensionException(String message, Throwable cause) {
        super(message, cause);
    }
}
