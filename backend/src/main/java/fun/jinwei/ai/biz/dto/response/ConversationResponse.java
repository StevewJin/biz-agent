package fun.jinwei.ai.biz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private Long id;
    private String title;
    private String status;
    private String model;
    private String systemPrompt;
    private Long messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
