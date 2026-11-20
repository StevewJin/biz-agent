import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useChatStore } from '../../stores/chatStore';
import { useSkillStore } from '../../stores/skillStore';
import { Puzzle } from 'lucide-react';

/**
 * 聊天头部 - 显示对话标题、活跃技能和模型信息
 */
export default function ChatHeader() {
  const currentConversation = useChatStore((s) => s.currentConversation);
  const { activeSkills, loadSkills } = useSkillStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (currentConversation) {
      loadSkills(currentConversation.id);
    }
  }, [currentConversation, loadSkills]);

  if (!currentConversation) return null;

  return (
    <header className="h-14 bg-white border-b border-gray-200 flex items-center px-6 shrink-0">
      <div className="flex-1 min-w-0 flex items-center gap-3">
        <h2 className="text-sm font-medium text-gray-800 truncate">
          {currentConversation.title}
        </h2>
        {/* 活跃技能标签 */}
        {activeSkills.length > 0 && (
          <div className="flex items-center gap-1.5">
            {activeSkills.map((skill) => (
              <span
                key={skill.id}
                className="text-xs bg-purple-50 text-purple-600 px-2 py-0.5 rounded-full border border-purple-200"
              >
                {skill.displayName}
              </span>
            ))}
          </div>
        )}
      </div>
      <div className="flex items-center gap-3 ml-4">
        {/* 技能市场入口 */}
        <button
          onClick={() => navigate(`/skills?conversationId=${currentConversation.id}`)}
          className="flex items-center gap-1 text-xs text-gray-400 hover:text-purple-600 cursor-pointer transition"
          title="技能市场"
        >
          <Puzzle size={14} />
          技能
        </button>
        {/* 模型标签 */}
        <span className="text-xs bg-gray-100 text-gray-500 px-2.5 py-1 rounded-full">
          {currentConversation.model}
        </span>
        {/* 消息数 */}
        <span className="text-xs text-gray-400">
          {currentConversation.messageCount} 条消息
        </span>
      </div>
    </header>
  );
}
