package fun.jinwei.ai.biz.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送消息请求 (通过 WebSocket)
 */
@Data
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
