package fun.jinwei.ai.biz.service.tool;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 工具执行上下文 - 携带当前对话、用户与本次运行的能力作用域。
 */
@Data
@Builder
public class ExecutionContext {
    /** 对话 ID */
    private Long conversationId;
    /** 用户 ID */
    private Long userId;
    /** 团队 ID (个人上下文为 null) */
    private Long teamId;
    /** 用户名 */
    private String username;
    /** 是否匿名用户 (未登录且 URL 中无用户信息) */
    @Builder.Default
    private boolean anonymous = false;
    /** 本次运行允许访问的数据源编码；为空表示不限制（仍受用户可见性约束） */
    @Builder.Default
    private Set<String> allowedDatasourceCodes = Set.of();
    /** 本次运行允许调用的接口编码；为空表示不限制 */
    @Builder.Default
    private Set<String> allowedApiCodes = Set.of();
}
