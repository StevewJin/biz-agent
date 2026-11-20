package fun.jinwei.ai.biz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.dto.response.MessageResponse;
import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.entity.Message;
import fun.jinwei.ai.biz.entity.ThinkingChainEntry;
import fun.jinwei.ai.biz.entity.enums.MessageRole;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.repository.MessageRepository;
import fun.jinwei.ai.biz.repository.ThinkingChainEntryRepository;
import fun.jinwei.ai.biz.service.tool.ToolDisplaySanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_TEXT_LENGTH = 5000;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ThinkingChainEntryRepository thinkingChainRepository;
    private final ToolDisplaySanitizer toolSanitizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 获取对话内消息列表 (分页) — 含思维链 */
    public Page<MessageResponse> listMessages(Long conversationId, Pageable pageable) {
        Page<Message> page = messageRepository
                .findByConversationIdOrderBySequenceNumAsc(conversationId, pageable);

        // 批量加载思维链
        List<Long> messageIds = page.stream().map(Message::getId).toList();
        Map<Long, List<ThinkingChainEntry>> chainMap = new HashMap<>();
        if (!messageIds.isEmpty()) {
            List<ThinkingChainEntry> allEntries = thinkingChainRepository
                    .findByMessageIdInOrderBySequenceNumAsc(messageIds);
            for (ThinkingChainEntry entry : allEntries) {
                chainMap.computeIfAbsent(entry.getMessage().getId(), k -> new ArrayList<>()).add(entry);
            }
        }

        return page.map(m -> toResponse(m, chainMap.getOrDefault(m.getId(), List.of())));
    }

    /** 保存用户消息 */
    @Transactional
    public Message saveUserMessage(Conversation conversation, String content) {
        int nextSeq = getNextSequenceNum(conversation.getId());
        List<String> chunks = splitContent(content);
        Message first = null;
        for (int i = 0; i < chunks.size(); i++) {
            Message msg = messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .role(MessageRole.user)
                    .content(chunks.get(i))
                    .sequenceNum(nextSeq + i)
                    .build());
            if (i == 0) first = msg;
        }
        return first;
    }

    /** 保存最终 AI 助手消息 (不含中间步骤) */
    @Transactional
    public Message saveAssistantMessage(Conversation conversation, String content,
                                         String reasoningContent) {
        int nextSeq = getNextSequenceNum(conversation.getId());
        List<String> contentChunks = splitContent(content);
        List<String> reasoningChunks = splitContent(reasoningContent);
        int totalChunks = Math.max(contentChunks.size(), reasoningChunks.size());
        Message first = null;
        for (int i = 0; i < totalChunks; i++) {
            String chunkContent = i < contentChunks.size() ? contentChunks.get(i) : null;
            String chunkReasoning = i < reasoningChunks.size() ? reasoningChunks.get(i) : null;
            Message msg = messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .role(MessageRole.assistant)
                    .content(chunkContent)
                    .reasoningContent(chunkReasoning)
                    .sequenceNum(nextSeq + i)
                    .build());
            if (i == 0) first = msg;
        }
        return first;
    }

    /** 批量保存思维链条目，超过 MAX_TEXT_LENGTH 的内容自动分割为多条记录 */
    @Transactional
    public void saveThinkingChain(Message message, List<ThinkingChainEntry> entries) {
        if (entries.isEmpty()) return;
        List<ThinkingChainEntry> expanded = new ArrayList<>();
        int seq = 0;
        for (ThinkingChainEntry entry : entries) {
            List<String> chunks = splitContent(entry.getContent());
            for (int i = 0; i < chunks.size(); i++) {
                expanded.add(ThinkingChainEntry.builder()
                        .message(message)
                        .sequenceNum(seq++)
                        .type(entry.getType())
                        .content(chunks.get(i))
                        .toolCallId(entry.getToolCallId())
                        .toolName(entry.getToolName())
                        .toolInput(entry.getToolInput())
                        .toolDisplayName(entry.getToolDisplayName())
                        .extensionCode(entry.getExtensionCode())
                        .extensionName(entry.getExtensionName())
                        .toolKind(entry.getToolKind())
                        .skillName(entry.getSkillName())
                        .durationMs(entry.getDurationMs())
                        .renderJson(i == chunks.size() - 1 ? entry.getRenderJson() : null)
                        .build());
            }
        }
        thinkingChainRepository.saveAll(expanded);
    }

    /** 加载消息的思维链 (给 LLM 上下文重建用) */
    public List<ThinkingChainEntry> getThinkingChain(Long messageId) {
        return thinkingChainRepository.findByMessageIdOrderBySequenceNumAsc(messageId);
    }

    private int getNextSequenceNum(Long conversationId) {
        Integer maxSeq = messageRepository.findMaxSequenceNum(conversationId);
        return (maxSeq != null ? maxSeq : 0) + 1;
    }

    private MessageResponse toResponse(Message m, List<ThinkingChainEntry> entries) {
        String originalToolName = m.getToolName();
        return MessageResponse.builder()
                .id(m.getId())
                .role(m.getRole().name())
                .content(m.getContent())
                .reasoningContent(m.getReasoningContent())
                .toolCallId(m.getToolCallId())
                .toolName(originalToolName)
                .toolInput(originalToolName != null && m.getToolInput() != null
                        ? parseJsonToMap(toolSanitizer.sanitizeInput(originalToolName, m.getToolInput())) : null)
                .toolResult(originalToolName != null && m.getToolResult() != null
                        ? toolSanitizer.sanitizeResult(originalToolName, m.getToolResult()) : null)
                .sequenceNum(m.getSequenceNum())
                .createdAt(m.getCreatedAt())
                .thinkingChain(entries.stream().map(this::toChainResponse).filter(Objects::nonNull).toList())
                .build();
    }

    /** @return null 当条目脱敏后内容为空（需在上层过滤） */
    private MessageResponse.ThinkingChainEntryResponse toChainResponse(ThinkingChainEntry e) {
        String type = e.getType();
        String originalToolName = e.getToolName();
        String displayContent = e.getContent();
        Map<String, Object> displayToolInput = parseJsonToMap(e.getToolInput());

        if ("thinking".equals(type) || "text".equals(type)) {
            displayContent = toolSanitizer.sanitizeThinkingContent(e.getContent());
            if (displayContent != null && displayContent.replace("*", "").replaceAll("\\s+", "").isEmpty()) {
                return null; // 脱敏后无实质内容，丢弃
            }
        } else if ("tool_call".equals(type) && originalToolName != null) {
            displayToolInput = parseJsonToMap(toolSanitizer.sanitizeInput(originalToolName, e.getToolInput()));
        } else if ("tool_result".equals(type) && originalToolName != null) {
            displayContent = toolSanitizer.sanitizeResult(originalToolName, e.getContent());
        }

        return MessageResponse.ThinkingChainEntryResponse.builder()
                .id(e.getId())
                .sequenceNum(e.getSequenceNum())
                .type(type)
                .content(displayContent)
                .toolCallId(e.getToolCallId())
                .toolName(originalToolName)
                .toolDisplayName(e.getToolDisplayName())
                .extensionCode(e.getExtensionCode())
                .extensionName(e.getExtensionName())
                .toolKind(e.getToolKind())
                .skillName(e.getSkillName())
                .toolInput(displayToolInput)
                .durationMs(e.getDurationMs())
                .renderJson(e.getRenderJson())
                .build();
    }

    private List<String> splitContent(String content) {
        if (content == null || content.isEmpty() || content.length() <= MAX_TEXT_LENGTH) {
            return Collections.singletonList(content);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + MAX_TEXT_LENGTH, content.length());
            chunks.add(content.substring(start, end));
            start = end;
        }
        return chunks;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
