package fun.jinwei.ai.biz.exception;

import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.extension.api.ExtensionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return ResponseEntity.status(ex.getCode())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /** 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, message));
    }

    /** 认证异常 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return ResponseEntity.status(401)
                .body(ApiResponse.error(401, "认证失败，请重新登录"));
    }

    /** 权限异常 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return ResponseEntity.status(403)
                .body(ApiResponse.error(403, "权限不足"));
    }

    /** 参数/业务校验异常（数据源、接口、扩展包、技能包等） */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(RuntimeException ex) {
        log.warn("请求参数异常: {}", ex.getMessage());
        String message = ex.getMessage() != null ? ex.getMessage() : "请求参数不合法";
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }

    /** 扩展包工具与数据源执行异常（参数错误、越权、SQL 被拒等） */
    @ExceptionHandler(ExtensionException.class)
    public ResponseEntity<ApiResponse<Void>> handleExtension(ExtensionException ex) {
        log.warn("扩展执行异常: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage() != null ? ex.getMessage() : "执行失败"));
    }

    /** 其他未处理异常 */
    /** 未匹配的接口/静态资源，返回 404 而不是 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, "接口或资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("未处理异常: ", ex);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}
