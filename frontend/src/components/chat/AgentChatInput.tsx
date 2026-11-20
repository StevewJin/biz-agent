import { useState, type KeyboardEvent } from 'react';
import { usePublicAgentStore } from '../../stores/publicAgentStore';
import { Send, Square } from 'lucide-react';

export default function AgentChatInput() {
  const [input, setInput] = useState('');
  const sendMessage = usePublicAgentStore((s) => s.sendMessage);
  const isStreaming = usePublicAgentStore((s) => s.isStreaming);
  const currentConversation = usePublicAgentStore((s) => s.currentConversation);

  const handleSend = () => {
    const content = input.trim();
    if (!content || !currentConversation || isStreaming) return;
    sendMessage(content);
    setInput('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="p-4 bg-white border-t border-gray-200 shrink-0">
      <div className="flex items-end gap-3 max-w-4xl mx-auto">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={currentConversation ? '输入消息... (Enter 发送, Shift+Enter 换行)' : '请先选择或创建对话'}
          disabled={!currentConversation || isStreaming}
          rows={1}
          className="flex-1 resize-none border border-gray-300 rounded-xl px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent disabled:bg-gray-50 disabled:text-gray-400 max-h-32"
          style={{ minHeight: '44px' }}
        />

        <button
          onClick={isStreaming ? () => {} : handleSend}
          disabled={!currentConversation || !input.trim()}
          className="p-3 bg-purple-600 text-white rounded-xl hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed transition cursor-pointer shrink-0"
          title={isStreaming ? '回复中...' : '发送'}
        >
          {isStreaming ? (
            <Square size={18} />
          ) : (
            <Send size={18} />
          )}
        </button>
      </div>
    </div>
  );
}
