import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import * as api from '../services/api';
import type { AgentPublicInfoFull } from '../services/api';
import { usePublicAgentStore } from '../stores/publicAgentStore';
import AgentMessageList from '../components/chat/AgentMessageList';
import AgentChatInput from '../components/chat/AgentChatInput';
import { Bot, AlertTriangle, MessageSquarePlus, User, Globe, MessageSquare } from 'lucide-react';

function generateUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for insecure contexts (non-HTTPS, non-localhost)
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function getSessionId(): string {
  const key = 'bizagent-public-session';
  let id = localStorage.getItem(key);
  if (!id) { id = generateUUID(); localStorage.setItem(key, id); }
  return id;
}

export default function AgentPublicPage() {
  const { shareToken } = useParams<{ shareToken: string }>();
  const [searchParams] = useSearchParams();
  const encryptedUser = searchParams.get('user') || undefined;
  const sessionId = getSessionId();

  const store = usePublicAgentStore;
  const conversations = store((s) => s.conversations);
  const conversationsLoading = store((s) => s.conversationsLoading);
  const currentConversation = store((s) => s.currentConversation);
  const init = store((s) => s.init);
  const loadConversations = store((s) => s.loadConversations);
  const selectConversation = store((s) => s.selectConversation);
  const createConversation = store((s) => s.createConversation);
  const error = store((s) => s.error);

  const [agent, setAgent] = useState<AgentPublicInfoFull | null>(null);
  const [decryptedUsername, setDecryptedUsername] = useState<string | null>(null);
  const [limited, setLimited] = useState(false);
  const [pageError, setPageError] = useState('');

  // 初始化 store
  useEffect(() => {
    if (!shareToken) return;
    init(shareToken, sessionId, encryptedUser);
  }, [shareToken, sessionId, encryptedUser, init]);

  // 加载智能体信息
  useEffect(() => {
    if (!shareToken) return;
    api.getAgentPublicInfo(shareToken, encryptedUser).then((r) => {
      setAgent(r.data);
      setDecryptedUsername(r.data.decryptedUsername);
      loadConversations();
    }).catch(() => setPageError('智能体不存在或已下线'));
  }, [shareToken, encryptedUser, loadConversations]);

  // 设置浏览器标题为智能体名称
  useEffect(() => {
    if (agent?.name) {
      document.title = agent.name;
    }
    return () => { document.title = 'BizAgent'; };
  }, [agent?.name]);

  const handleNewConversation = async () => {
    if (limited) return;
    try {
      await createConversation();
    } catch (e: unknown) {
      const msg = (e as Error).message;
      if (msg.includes('仅支持')) { setLimited(true); setPageError(msg); }
    }
  };

  // 错误页面
  if (pageError && !agent) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <AlertTriangle size={48} className="text-red-300 mx-auto mb-4" />
          <p className="text-gray-600">{pageError}</p>
          <a href="/login" className="text-purple-600 text-sm hover:underline mt-2 inline-block">登录后使用</a>
        </div>
      </div>
    );
  }

  const isUnlimited = !!decryptedUsername;

  return (
    <div className="h-screen flex bg-gray-50">
      {/* 左侧边栏 - 匹配 ChatSidebar 结构和样式 */}
      <aside className="w-72 h-full bg-white border-r border-gray-200 flex flex-col shrink-0">
        {/* 头部 - 智能体信息 + 新建对话按钮 */}
        <div className="p-4 border-b border-gray-100">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2 min-w-0">
              {agent?.logo ? (
                <img src={agent.logo} alt="" className="w-6 h-6 rounded-lg shrink-0" />
              ) : (
                <div className="w-6 h-6 rounded-lg bg-purple-100 flex items-center justify-center shrink-0">
                  <Bot size={14} className="text-purple-600" />
                </div>
              )}
              <h1 className="text-lg font-bold text-purple-600 truncate">{agent?.name || '智能助手'}</h1>
            </div>
            <button
              onClick={handleNewConversation}
              disabled={limited && !isUnlimited}
              className="p-2 text-gray-500 hover:text-purple-600 hover:bg-purple-50 rounded-lg transition cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              title="新建对话"
            >
              <MessageSquarePlus size={20} />
            </button>
          </div>

          <button
            onClick={handleNewConversation}
            disabled={limited && !isUnlimited}
            className="w-full py-2 text-sm text-purple-600 border border-purple-200 rounded-lg hover:bg-purple-50 transition cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            + 新建对话
          </button>
        </div>

        {/* 对话列表 - 匹配 ConversationList 结构和样式 */}
        <div className="flex-1 overflow-y-auto">
          {conversationsLoading ? (
            <div className="p-4 text-center text-gray-400 text-sm">加载中...</div>
          ) : conversations.length === 0 ? (
            <div className="p-4 text-center text-gray-400 text-sm">暂无对话，点击上方按钮创建</div>
          ) : (
            <div className="py-2">
              {conversations.map((c) => {
                const isActive = currentConversation?.id === c.id;
                return (
                  <div
                    key={c.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => selectConversation(c.id)}
                    onKeyDown={(e) => { if (e.key === 'Enter') selectConversation(c.id); }}
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
                        {c.title}
                      </div>
                      <div className="text-xs text-gray-400 mt-0.5">{c.messageCount} 条消息</div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* 底部状态 - 匹配 UserMenu 结构 */}
        <div className="p-4 border-t border-gray-100">
          <div className="flex items-center gap-3">
            <div className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${isUnlimited ? 'bg-green-100' : 'bg-orange-100'}`}>
              {isUnlimited ? <User size={16} className="text-green-600" /> : <Globe size={16} className="text-orange-500" />}
            </div>
            <div className="flex-1 min-w-0">
              {isUnlimited ? (
                <div className="text-sm font-medium text-gray-700 truncate">{decryptedUsername}</div>
              ) : (
                <div className="text-sm font-medium text-gray-700">未登录用户</div>
              )}
            </div>
          </div>
          {!isUnlimited && !limited && (
            <p className="text-xs text-gray-400 mt-1.5">限 1 个对话，3 条消息</p>
          )}
          {limited && (
            <div className="mt-2">
              <p className="text-xs text-orange-600">已达免费上限</p>
              <a href="/login" className="text-xs text-purple-600 hover:underline">登录后无限使用</a>
            </div>
          )}
          {error && (
            <p className="text-xs text-red-500 mt-1">{error}</p>
          )}
        </div>
      </aside>

      {/* 右侧聊天区 - 匹配 ChatWindow 结构 */}
      {!currentConversation ? (
        <div className="flex-1 flex items-center justify-center bg-gray-50">
          <div className="text-center">
            <div className="w-16 h-16 bg-purple-100 rounded-full flex items-center justify-center mx-auto mb-4">
              {agent?.logo ? (
                <img src={agent.logo} alt="" className="w-10 h-10 rounded-full" />
              ) : (
                <Bot size={32} className="text-purple-400" />
              )}
            </div>
            <h2 className="text-xl font-medium text-gray-700 mb-2">{agent?.name || '智能助手'}</h2>
            <p className="text-gray-400 text-sm mb-4">{agent?.description || '选择一个对话或创建新对话开始'}</p>
            <button
              onClick={handleNewConversation}
              disabled={limited && !isUnlimited}
              className="px-4 py-2 bg-purple-600 text-white text-sm rounded-lg hover:bg-purple-700 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              开始对话
            </button>
          </div>
        </div>
      ) : (
        <div className="flex-1 flex flex-col min-w-0">
          {/* 头部 - 匹配 ChatHeader 结构 */}
          <header className="h-14 bg-white border-b border-gray-200 flex items-center px-6 shrink-0">
            <div className="flex-1 min-w-0">
              <h2 className="text-sm font-medium text-gray-800 truncate">
                {currentConversation.title}
              </h2>
            </div>
            <div className="flex items-center gap-3 ml-4">
              {isUnlimited ? (
                <span className="text-xs bg-green-50 text-green-600 px-2 py-0.5 rounded-full">{decryptedUsername}</span>
              ) : (
                <span className="text-xs bg-orange-50 text-orange-500 px-2 py-0.5 rounded-full">未登录</span>
              )}
              {limited && <span className="text-xs bg-red-50 text-red-500 px-2 py-0.5 rounded-full">已达上限</span>}
              <span className="text-xs text-gray-400">{currentConversation.messageCount} 条消息</span>
            </div>
          </header>

          {/* 消息列表 */}
          <AgentMessageList />

          {/* 输入框 */}
          <AgentChatInput />
        </div>
      )}
    </div>
  );
}
