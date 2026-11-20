package fun.jinwei.ai.biz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话记录详情：会话元信息 + 完整消息（含思维链）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRecordDetailResponse {

    private ConversationRecordResponse conversation;

    private List<MessageResponse> messages;
}
