package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.request.ConversationRequest;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.dto.response.ConversationResponse;
import fun.jinwei.ai.biz.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 对话接口
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 获取当前用户对话列表 */
    @GetMapping
    public ApiResponse<Page<ConversationResponse>> listConversations(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = getUserId(auth);
        return ApiResponse.success(conversationService.listUserConversations(userId, pageable));
    }

    /** 获取对话详情 */
    @GetMapping("/{id}")
    public ApiResponse<ConversationResponse> getConversation(
            Authentication auth,
            @PathVariable Long id) {
        Long userId = getUserId(auth);
        return ApiResponse.success(conversationService.getConversation(id, userId));
    }

    /** 创建新对话 */
    @PostMapping
    public ApiResponse<ConversationResponse> createConversation(
            Authentication auth,
            @Valid @RequestBody ConversationRequest request) {
        Long userId = getUserId(auth);
        return ApiResponse.success(conversationService.createConversation(userId, request));
    }

    /** 更新对话 */
    @PutMapping("/{id}")
    public ApiResponse<ConversationResponse> updateConversation(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody ConversationRequest request) {
        Long userId = getUserId(auth);
        return ApiResponse.success(conversationService.updateConversation(id, userId, request));
    }

    /** 删除对话 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConversation(
            Authentication auth,
            @PathVariable Long id) {
        Long userId = getUserId(auth);
        conversationService.deleteConversation(id, userId);
        return ApiResponse.success();
    }

    /** 从认证对象中提取用户 ID */
    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
