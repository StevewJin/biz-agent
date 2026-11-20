package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.dto.response.MessageResponse;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.service.MessageService;
import fun.jinwei.ai.biz.service.conversation.ConversationRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 消息接口
 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ConversationRecordService conversationRecordService;

    /** 获取对话消息列表 (分页，按时间升序) */
    @GetMapping
    public ApiResponse<Page<MessageResponse>> listMessages(
            Authentication auth,
            @PathVariable Long conversationId,
            @PageableDefault(size = 50) Pageable pageable) {
        // 会话归属校验：本人 / 团队管理员 / 系统管理员
        conversationRecordService.assertConversationVisible(
                conversationId, AccessGuard.userId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success(messageService.listMessages(conversationId, pageable));
    }
}
