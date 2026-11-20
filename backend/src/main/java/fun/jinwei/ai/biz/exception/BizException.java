package fun.jinwei.ai.biz.exception;

import lombok.Getter;

/**
 * 业务异常 - 用于返回明确的业务错误信息
 */
@Getter
public class BizException extends RuntimeException {

    /** HTTP 状态码 */
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        super(message);
        this.code = 500;
    }
}
