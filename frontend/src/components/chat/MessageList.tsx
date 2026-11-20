import { useEffect, useRef } from 'react';
import { useChatStore } from '../../stores/chatStore';
import MessageBubble from './MessageBubble';
import ToolCallCard from './ToolCallCard';

export default function MessageList() {
  const messages = useChatStore((s) => s.messages);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const streamingContent = useChatStore((s) => s.streamingContent);
  const streamingThinking = useChatStore((s) => s.streamingThinking);
  const streamingToolCalls = useChatStore((s) => s.streamingToolCalls);
  const error = useChatStore((s) => s.error);
  const clearError = useChatStore((s) => s.clearError);
  const messagesLoading = useChatStore((s) => s.messagesLoading);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingContent, streamingToolCalls]);

  if (messagesLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-gray-400 text-sm">加载消息中...</div>
      </div>
    );
  }

  const hasStreamingContent = isStreaming && (streamingContent || streamingThinking || streamingToolCalls.length > 0);

  return (
    <div className="flex-1 overflow-y-auto px-4 py-6">
      {messages.length === 0 && !hasStreamingContent && (
        <div className="text-center text-gray-400 text-sm mt-8">
          发送消息开始对话
        </div>
      )}

      {messages.map((msg) => (
        <MessageBubble key={msg.id} message={msg} />
      ))}

      {/* 错误提示 */}
      {error && (
        <div className="flex justify-center mb-4">
          <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-2 rounded-lg">
            <span>{error}</span>
            <button
              onClick={clearError}
              className="text-red-400 hover:text-red-600 cursor-pointer font-medium"
            >
              x
            </button>
          </div>
        </div>
      )}

      {/* 流式输出中的临时气泡 */}
      {hasStreamingContent && (
        <div className="flex gap-3 mb-4">
          <div className="w-8 h-8 bg-purple-100 rounded-full flex items-center justify-center shrink-0 mt-1">
            <span className="text-purple-600 text-xs font-bold">AI</span>
          </div>
          <div className="max-w-[80%] min-w-[200px] space-y-2">
            {/* 工具调用卡片 */}
            {streamingToolCalls.map((tc) => (
              <ToolCallCard
                key={tc.toolCallId}
                toolName={tc.toolName}
                toolDisplayName={tc.toolDisplayName}
                extensionCode={tc.extensionCode}
                extensionName={tc.extensionName}
                toolKind={tc.toolKind}
                skillName={tc.skillName}
                toolInput={tc.toolInput ? safeParse(tc.toolInput) : null}
                toolResult={tc.result}
                render={tc.render}
                status={tc.status}
                durationMs={tc.status === 'done' ? tc.durationMs : null}
              />
            ))}

            {/* 思维链（推理过程） */}
            {streamingThinking && (
              <details open className="mb-2">
                <summary className="text-xs text-gray-500 cursor-pointer hover:text-gray-700">
                  思考中...
                </summary>
                <div className="mt-1 p-2 bg-gray-50 rounded-lg text-xs text-gray-500 whitespace-pre-wrap break-words max-h-48 overflow-y-auto">
                  {streamingThinking}
                </div>
              </details>
            )}

            {/* 文本回复 */}
            {streamingContent && (
              <div className="bg-gray-100 rounded-2xl rounded-tl-md px-4 py-3">
                <div className="text-sm text-gray-800 whitespace-pre-wrap break-words">
                  {streamingContent}
                  {streamingToolCalls.length === 0 && (
                    <span className="inline-block w-2 h-4 bg-purple-500 animate-pulse ml-0.5 align-middle" />
                  )}
                </div>
              </div>
            )}

            {/* 闪烁"思考中..." */}
            <div className="flex items-center gap-1.5 mt-2">
              <span className="w-1.5 h-1.5 bg-purple-400 rounded-full animate-pulse" />
              <span className="text-xs text-purple-400 animate-pulse">思考中...</span>
            </div>
          </div>
        </div>
      )}

      {/* 初始加载指示器 */}
      {isStreaming && !hasStreamingContent && (
        <div className="flex gap-3 mb-4">
          <div className="w-8 h-8 bg-purple-100 rounded-full flex items-center justify-center shrink-0">
            <span className="text-purple-600 text-xs font-bold">AI</span>
          </div>
          <div className="bg-gray-100 rounded-2xl rounded-tl-md px-4 py-3">
            <span className="text-sm text-purple-400 animate-pulse">思考中...</span>
          </div>
        </div>
      )}

      <div ref={bottomRef} />
    </div>
  );
}

function safeParse(json: string): Record<string, unknown> | null {
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
}
