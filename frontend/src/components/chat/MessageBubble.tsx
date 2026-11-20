import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Message, ThinkingChainEntry } from '../../types';
import type { RenderDescriptor } from '../../extensions/types';
import ToolCallCard from './ToolCallCard';
import ExtensionRenderer from '../extensions/ExtensionRenderer';

interface MessageBubbleProps {
  message: Message;
}

export default function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === 'user';
  const isAssistant = message.role === 'assistant';
  const chain = message.thinkingChain;

  // 按顺序渲染: text/thinking → markdown, tool_call+tool_result → ToolCallCard
  const orderedItems = buildOrderedItems(chain);
  const hasThinkingChain = orderedItems.length > 0;

  // 思维链中的所有渲染负载（图表 / 表格 / 导出卡片 / 扩展包自定义渲染）
  const inlineRenders = extractRenders(chain);

  return (
    <div className={`flex gap-3 mb-4 ${isUser ? 'flex-row-reverse' : ''}`}>
      <div className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 mt-1 ${
        isUser ? 'bg-blue-100' : 'bg-purple-100'
      }`}>
        <span className={`text-xs font-bold ${
          isUser ? 'text-blue-600' : 'text-purple-600'
        }`}>
          {isUser ? '我' : 'AI'}
        </span>
      </div>

      <div className={`rounded-2xl px-4 py-3 max-w-[80%] ${
        isUser
          ? 'bg-blue-600 text-white rounded-tr-md'
          : 'bg-gray-100 text-gray-800 rounded-tl-md'
      }`}>
        {/* 思维链 */}
        {isAssistant && hasThinkingChain && (
          <details className="mb-2">
            <summary className="text-xs text-purple-600 cursor-pointer font-medium select-none hover:text-purple-700">
              已深度思考
            </summary>
            <div className="mt-2 pl-3 border-l-2 border-purple-200 space-y-2">
              {orderedItems.map((item, i) => {
                if (item.type === 'text') {
                  return (
                    <div key={`t-${i}`} className="text-xs text-gray-500 prose prose-xs max-w-none prose-p:my-1">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{item.content || ''}</ReactMarkdown>
                    </div>
                  );
                }
                if (item.type === 'tool') {
                  return (
                    <ToolCallCard
                      key={item.call?.toolCallId || `tool-${i}`}
                      toolName={item.call?.toolName || 'unknown'}
                      toolDisplayName={item.call?.toolDisplayName}
                      extensionCode={item.call?.extensionCode}
                      extensionName={item.call?.extensionName}
                      toolKind={item.call?.toolKind}
                      skillName={item.call?.skillName}
                      toolInput={item.call?.toolInput ?? null}
                      toolResult={item.result?.content || undefined}
                      render={item.result?.renderJson || undefined}
                      showRender={false}
                      status="done"
                      durationMs={item.result?.durationMs ?? undefined}
                    />
                  );
                }
                return null;
              })}
            </div>
          </details>
        )}

        {/* 最终回答 */}
        {isAssistant ? (
          <div className="text-sm prose prose-sm max-w-none prose-p:my-1 prose-pre:bg-gray-800 prose-pre:text-gray-100 prose-code:text-purple-600 prose-code:bg-purple-50 prose-code:px-1 prose-code:rounded">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content || ''}</ReactMarkdown>
          </div>
        ) : (
          <div className="text-sm whitespace-pre-wrap break-words">
            {message.content}
          </div>
        )}

        {/* 内联渲染（图表 / 表格 / 导出 / 扩展包渲染器） */}
        {inlineRenders.length > 0 && (
          <div className="mt-3 space-y-3">
            {inlineRenders.map((render, i) => (
              <div key={`${render.type}-${i}`} className="bg-white rounded-lg border border-gray-200 p-3">
                <ExtensionRenderer render={render} />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** 从思维链中提取所有 _render 渲染负载 */
export function extractRenders(chain: ThinkingChainEntry[] | null): RenderDescriptor[] {
  if (!chain || chain.length === 0) return [];
  const renders: RenderDescriptor[] = [];
  const seen = new Set<string>();

  for (const entry of chain) {
    if (entry.type !== 'tool_result' || !entry.renderJson) continue;
    const key = `${entry.toolCallId || entry.sequenceNum}`;
    if (seen.has(key)) continue;
    try {
      const parsed = JSON.parse(entry.renderJson);
      const payload = parsed?._render;
      if (payload && typeof payload.type === 'string') {
        renders.push({ type: payload.type, props: payload.props || {} });
        seen.add(key);
      }
    } catch {
      // 非渲染负载，忽略
    }
  }
  return renders;
}

/** 将思维链数组转为按时间顺序的展示项 */
function buildOrderedItems(chain: ThinkingChainEntry[] | null): OrderedItem[] {
  if (!chain || chain.length === 0) return [];

  const items: OrderedItem[] = [];
  const used = new Set<number>();

  for (let i = 0; i < chain.length; i++) {
    if (used.has(i)) continue;
    const entry = chain[i];

    if (entry.type === 'text' || entry.type === 'thinking') {
      items.push({ type: 'text', content: entry.content });
    } else if (entry.type === 'tool_call' && entry.toolCallId) {
      // 查找后续匹配的 tool_result
      let result: ThinkingChainEntry | undefined;
      for (let j = i + 1; j < chain.length; j++) {
        if (chain[j].type === 'tool_result' && chain[j].toolCallId === entry.toolCallId) {
          result = chain[j];
          used.add(j);
          break;
        }
      }
      items.push({ type: 'tool', call: entry, result });
    }
    // 孤立的 tool_result (没有前置 tool_call) 跳过
  }

  return items;
}

interface OrderedItem {
  type: 'text' | 'tool';
  content?: string | null;
  call?: ThinkingChainEntry;
  result?: ThinkingChainEntry;
}
