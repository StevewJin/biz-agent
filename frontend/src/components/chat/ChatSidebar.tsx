import { useChatStore } from '../../stores/chatStore';
import ConversationList from './ConversationList';
import UserMenu from './UserMenu';
import { MessageSquarePlus } from 'lucide-react';

interface ChatSidebarProps {
  userName: string;
}

/**
 * 左侧边栏 - 对话列表 + 用户菜单
 */
export default function ChatSidebar({ userName }: ChatSidebarProps) {
  const createConversation = useChatStore((s) => s.createConversation);

  const handleNewChat = async () => {
    await createConversation();
  };

  return (
    <aside className="w-72 h-full bg-white border-r border-gray-200 flex flex-col shrink-0">
      {/* 头部 */}
      <div className="p-4 border-b border-gray-100">
        <div className="flex items-center justify-between mb-3">
          <h1 className="text-lg font-bold text-purple-600">BizAgent</h1>
          <button
            onClick={handleNewChat}
            className="p-2 text-gray-500 hover:text-purple-600 hover:bg-purple-50 rounded-lg transition cursor-pointer"
            title="新建对话"
          >
            <MessageSquarePlus size={20} />
          </button>
        </div>

        {/* 新对话按钮 */}
        <button
          onClick={handleNewChat}
          className="w-full py-2 text-sm text-purple-600 border border-purple-200 rounded-lg hover:bg-purple-50 transition cursor-pointer"
        >
          + 新建对话
        </button>
      </div>

      {/* 对话列表 */}
      <div className="flex-1 overflow-y-auto">
        <ConversationList />
      </div>

      {/* 底部用户菜单 */}
      <UserMenu userName={userName} />
    </aside>
  );
}
