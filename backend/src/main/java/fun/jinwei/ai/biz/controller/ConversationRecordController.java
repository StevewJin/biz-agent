package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.dto.response.ConversationRecordDetailResponse;
import fun.jinwei.ai.biz.dto.response.ConversationRecordResponse;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.service.conversation.ConversationRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 会话记录（管理视角）。
 *
 * <p>范围由 scope 决定：mine（本人）/ team（本团队）/ all（全部，仅管理员）。</p>
 */
@RestController
@RequestMapping("/api/conversation-records")
@RequiredArgsConstructor
public class ConversationRecordController {

    private final ConversationRecordService conversationRecordService;

    /** 当前账号可用的数据范围 */
    @GetMapping("/scopes")
    public ApiResponse<ConversationRecordService.ScopeCapability> scopes(Authentication auth) {
        return ApiResponse.success(conversationRecordService.capabilities(
                AccessGuard.userId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 会话记录列表 */
    @GetMapping
    public ApiResponse<Page<ConversationRecordResponse>> list(
            Authentication auth,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(conversationRecordService.list(
                parseScope(scope), teamId, keyword, agentId, userId, status, from, to,
                includeDeleted, page, size,
                AccessGuard.userId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 会话详情（含完整消息与思维链） */
    @GetMapping("/{id}")
    public ApiResponse<ConversationRecordDetailResponse> detail(Authentication auth, @PathVariable Long id) {
        return ApiResponse.success(conversationRecordService.detail(
                id, AccessGuard.userId(auth), AccessGuard.isAdmin(auth)));
    }

    /** 软删除会话记录 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long id) {
        conversationRecordService.delete(id, AccessGuard.userId(auth), AccessGuard.isAdmin(auth));
        return ApiResponse.success();
    }

    private ConversationRecordService.Scope parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return ConversationRecordService.Scope.mine;
        }
        try {
            return ConversationRecordService.Scope.valueOf(scope.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ConversationRecordService.Scope.mine;
        }
    }
}
