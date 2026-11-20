import { useChatStore } from '../../stores/chatStore';
import ChatHeader from './ChatHeader';
import MessageList from './MessageList';
import ChatInput from './ChatInput';
import SkillTray from './SkillTray';
import { MessageSquare } from 'lucide-react';

/**
 * 主聊天窗口 - 包含头部、消息列表、技能托盘、输入框
 */
export default function ChatWindow() {
  const currentConversation = useChatStore((s) => s.currentConversation);

  // 未选择对话时的空状态
  if (!currentConversation) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="w-16 h-16 bg-purple-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <MessageSquare size={32} className="text-purple-400" />
          </div>
          <h2 className="text-xl font-medium text-gray-700 mb-2">
            BizAgent 智能业务助手
          </h2>
          <p className="text-gray-400 text-sm">
            选择一个对话或创建新对话开始
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col min-w-0">
      {/* 顶部 */}
      <ChatHeader />

      {/* 消息列表 */}
      <MessageList />

      {/* 技能托盘 */}
      <SkillTray />

      {/* 底部输入框 */}
      <ChatInput />
    </div>
  );
}
