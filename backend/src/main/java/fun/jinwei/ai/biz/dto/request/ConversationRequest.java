package fun.jinwei.ai.biz.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 对话创建/更新请求
 */
@Data
public class ConversationRequest {

    /** 对话标题 */
    @Size(max = 200, message = "标题最长 200 个字符")
    private String title;

    /** 使用的模型 */
    @Size(max = 100, message = "模型名最长 100 个字符")
    private String model;

    /** 自定义系统提示词 */
    private String systemPrompt;

    /** 所属团队；为空表示个人空间 */
    private Long teamId;

    /** 使用的智能体 */
    private Long agentId;
}
