package fun.jinwei.ai.biz.service.llm;

import reactor.core.publisher.Flux;

/**
 * LLM 网关接口 - 抽象不同 LLM 提供商的差异
 * 所有 LLM 实现 (Claude/OpenAI/DeepSeek/Ollama) 都需实现此接口
 */
public interface LlmGateway {

    /**
     * 流式聊天 - 返回 Server-Sent Events 事件流
     *
     * @param context 聊天上下文 (历史消息 + 系统提示 + 可用工具)
     * @return 流式响应块序列
     */
    Flux<StreamingChunk> streamChat(ChatContext context);

    /** 获取提供商名称 */
    String getProviderName();
}
