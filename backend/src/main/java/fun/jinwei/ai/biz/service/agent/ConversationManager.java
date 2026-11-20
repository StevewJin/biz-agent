package fun.jinwei.ai.biz.service.agent;

import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.entity.Message;
import fun.jinwei.ai.biz.entity.ThinkingChainEntry;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.repository.MessageRepository;
import fun.jinwei.ai.biz.service.MessageService;
import fun.jinwei.ai.biz.service.llm.ChatContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 对话管理器 - 从数据库加载对话历史，构建 LLM 上下文
 * 将 thinking_chain_entries 注入为合成消息 (tool_calls + tool results)
 */
@Service
@RequiredArgsConstructor
public class ConversationManager {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;

    public ChatContext buildContext(Long conversationId, String model) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在: " + conversationId));

        List<Message> historyMessages = messageRepository
                .findByConversationIdOrderBySequenceNumAsc(conversationId);

        // 批量加载所有消息的思维链
        Map<Long, List<ThinkingChainEntry>> chainMap = new HashMap<>();
        for (Message msg : historyMessages) {
            if ("assistant".equals(msg.getRole().name())) {
                List<ThinkingChainEntry> chain = messageService.getThinkingChain(msg.getId());
                if (!chain.isEmpty()) {
                    chainMap.put(msg.getId(), chain);
                }
            }
        }

        List<ChatContext.ChatMessage> chatMessages = new ArrayList<>();

        for (Message msg : historyMessages) {
            String role = msg.getRole().name();

            if ("tool".equals(role)) {
                continue; // tool 消息不再存入 messages 表, 跳过兼容
            }

            if ("user".equals(role)) {
                chatMessages.add(convertUserMessage(msg));
            } else if ("assistant".equals(role)) {
                List<ThinkingChainEntry> chain = chainMap.get(msg.getId());

                if (chain != null && !chain.isEmpty()) {
                    // 将思维链展开: 中间文本 → assistant(tool_calls) → tool results
                    injectThinkingChain(chatMessages, chain);
                }

                // 最终 assistant 回复
                chatMessages.add(convertAssistantMessage(msg));
            }
        }

        return ChatContext.builder()
                .model(model != null ? model : conversation.getModel())
                .systemPrompt(conversation.getSystemPrompt())
                .messages(chatMessages)
                .build();
    }

    private ChatContext.ChatMessage convertUserMessage(Message msg) {
        return ChatContext.ChatMessage.builder()
                .role("user")
                .content(msg.getContent())
                .build();
    }

    private ChatContext.ChatMessage convertAssistantMessage(Message msg) {
        ChatContext.ChatMessage.ChatMessageBuilder builder = ChatContext.ChatMessage.builder()
                .role("assistant")
                .content(msg.getContent());

        if (msg.getReasoningContent() != null && !msg.getReasoningContent().isBlank()) {
            builder.reasoningContent(msg.getReasoningContent());
        }

        return builder.build();
    }

    /**
     * 将思维链条目注入为 LLM 上下文中的合成消息
     * text/thinking → 跳过 (LLM 不需要看到中间文本)
     * tool_call + tool_result → 合成 assistant(tool_calls) + tool(result)
     */
    private void injectThinkingChain(List<ChatContext.ChatMessage> messages,
                                      List<ThinkingChainEntry> chain) {
        // 按 tool_call_id 分组: 收集每对 tool_call → tool_result
        List<ChatContext.ToolCall> toolCalls = new ArrayList<>();
        Map<String, String> toolResults = new LinkedHashMap<>(); // toolCallId → result

        for (ThinkingChainEntry entry : chain) {
            if ("tool_call".equals(entry.getType()) && entry.getToolCallId() != null) {
                toolCalls.add(ChatContext.ToolCall.builder()
                        .id(entry.getToolCallId())
                        .type("function")
                        .function(Map.of(
                                "name", entry.getToolName() != null ? entry.getToolName() : "unknown",
                                "arguments", entry.getToolInput() != null
                                        ? entry.getToolInput() : "{}"))
                        .build());
            } else if ("tool_result".equals(entry.getType()) && entry.getToolCallId() != null) {
                toolResults.put(entry.getToolCallId(),
                        entry.getContent() != null ? entry.getContent() : "");
            }
        }

        if (!toolCalls.isEmpty()) {
            // assistant 消息带 tool_calls
            messages.add(ChatContext.ChatMessage.builder()
                    .role("assistant")
                    .toolCalls(toolCalls)
                    .build());

            // tool 结果消息 (按 tool_calls 的顺序, 保持匹配)
            for (ChatContext.ToolCall tc : toolCalls) {
                String result = toolResults.getOrDefault(tc.getId(), "");
                messages.add(ChatContext.ChatMessage.builder()
                        .role("tool")
                        .toolCallId(tc.getId())
                        .content(result)
                        .build());
            }
        }
    }

}
