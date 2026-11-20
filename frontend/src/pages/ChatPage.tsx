import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';
import { useChatStore } from '../stores/chatStore';
import { wsManager } from '../services/websocket';
import ChatSidebar from '../components/chat/ChatSidebar';
import ChatWindow from '../components/chat/ChatWindow';

/**
 * 主聊天页面 - 侧边栏 + 聊天窗口布局
 */
export default function ChatPage() {
  const { conversationId } = useParams<{ conversationId: string }>();
  const user = useAuthStore((s) => s.user);
  const loadConversations = useChatStore((s) => s.loadConversations);
  const selectConversation = useChatStore((s) => s.selectConversation);

  // 初始化：加载对话列表 + 建立 WebSocket
  useEffect(() => {
    loadConversations();
    wsManager.connect();

    return () => {
      // 组件卸载时断开 WebSocket
      wsManager.disconnect();
    };
  }, [loadConversations]);

  // 如果 URL 中有 conversationId，自动选中
  useEffect(() => {
    if (conversationId) {
      selectConversation(Number(conversationId));
    }
  }, [conversationId, selectConversation]);

  return (
    <div className="h-screen flex bg-gray-50">
      {/* 左侧边栏 */}
      <ChatSidebar userName={user?.username || '用户'} />

      {/* 右侧聊天区 */}
      <ChatWindow />
    </div>
  );
}
