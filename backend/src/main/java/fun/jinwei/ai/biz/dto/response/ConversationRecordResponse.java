package fun.jinwei.ai.biz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话记录列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRecordResponse {

    private Long id;
    private String title;
    private String status;
    private String model;
    private Long messageCount;

    /** 创建人 */
    private Long userId;
    private String userName;

    /** 团队归属，个人空间为空 */
    private Long teamId;
    private String teamName;

    /** 使用的智能体 */
    private Long agentId;
    private String agentName;

    /** 当前用户对该会话的可管理权限（可删除） */
    private boolean manageable;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
