import { useChatStore } from '../../stores/chatStore';
import { MessageSquare, Trash2 } from 'lucide-react';

/**
 * 对话列表组件
 */
export default function ConversationList() {
  const conversations = useChatStore((s) => s.conversations);
  const currentConversation = useChatStore((s) => s.currentConversation);
  const selectConversation = useChatStore((s) => s.selectConversation);
  const deleteConversation = useChatStore((s) => s.deleteConversation);
  const conversationsLoading = useChatStore((s) => s.conversationsLoading);

  const handleDelete = (e: React.MouseEvent, id: number) => {
    e.stopPropagation();
    if (confirm('确认删除此对话？')) {
      deleteConversation(id);
    }
  };

  if (conversationsLoading) {
    return (
      <div className="p-4 text-center text-gray-400 text-sm">
        加载中...
      </div>
    );
  }

  if (conversations.length === 0) {
    return (
      <div className="p-4 text-center text-gray-400 text-sm">
        暂无对话，点击上方按钮创建
      </div>
    );
  }

  return (
    <div className="py-2">
      {conversations.map((conv) => {
        const isActive = currentConversation?.id === conv.id;
        return (
          <div
            key={conv.id}
            role="button"
            tabIndex={0}
            onClick={() => selectConversation(conv.id)}
            onKeyDown={(e) => { if (e.key === 'Enter') selectConversation(conv.id); }}
            className={`w-full text-left px-4 py-3 flex items-start gap-3 hover:bg-gray-50 transition group cursor-pointer ${
              isActive ? 'bg-purple-50 border-r-2 border-purple-500' : ''
            }`}
          >
            <MessageSquare
              size={18}
              className={`mt-0.5 shrink-0 ${isActive ? 'text-purple-500' : 'text-gray-400'}`}
            />
            <div className="flex-1 min-w-0">
              <div className={`text-sm truncate ${isActive ? 'text-purple-700 font-medium' : 'text-gray-700'}`}>
                {conv.title}
              </div>
              <div className="text-xs text-gray-400 mt-0.5">
                {conv.model} · {conv.messageCount} 条消息
              </div>
            </div>
            <button
              onClick={(e) => handleDelete(e, conv.id)}
              className="p-1 text-gray-300 hover:text-red-500 opacity-0 group-hover:opacity-100 transition cursor-pointer shrink-0"
              title="删除对话"
            >
              <Trash2 size={14} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
