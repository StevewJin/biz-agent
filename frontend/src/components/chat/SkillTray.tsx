import { useEffect } from 'react';
import { useChatStore } from '../../stores/chatStore';
import { useSkillStore } from '../../stores/skillStore';
import { Wrench, X } from 'lucide-react';

/**
 * 技能托盘 - 聊天页面底部技能开关面板
 */
export default function SkillTray() {
  const currentConversation = useChatStore((s) => s.currentConversation);
  const { skills, isLoading, loadSkills, toggleSkill } = useSkillStore();

  useEffect(() => {
    if (currentConversation) {
      loadSkills(currentConversation.id);
    }
  }, [currentConversation, loadSkills]);

  if (!currentConversation) return null;

  const activeSkills = skills.filter((s) => s.active);

  return (
    <div className="border-t border-gray-100 bg-gray-50/50 px-4 py-2">
      <div className="flex items-center gap-2 overflow-x-auto">
        <Wrench size={14} className="text-gray-400 shrink-0" />

        {isLoading && (
          <span className="text-xs text-gray-400">加载中...</span>
        )}

        {!isLoading && activeSkills.length === 0 && (
          <span className="text-xs text-gray-400">
            未激活技能 — 前往
            <a href="/skills" className="text-purple-600 hover:underline mx-1">
              技能市场
            </a>
            启用
          </span>
        )}

        {activeSkills.map((skill) => (
          <div
            key={skill.id}
            className="flex items-center gap-1 px-2 py-1 bg-purple-50 border border-purple-200 rounded-full text-xs text-purple-700 whitespace-nowrap"
          >
            <span>{skill.displayName}</span>
            <button
              onClick={() => toggleSkill(skill.id, currentConversation.id)}
              className="text-purple-400 hover:text-purple-600 cursor-pointer"
              title={`停用 ${skill.displayName}`}
            >
              <X size={12} />
            </button>
          </div>
        ))}

        {!isLoading && skills.some((s) => !s.active) && (
          <a
            href="/skills"
            className="text-xs text-purple-600 hover:text-purple-800 whitespace-nowrap ml-auto shrink-0"
          >
            + 添加技能
          </a>
        )}
      </div>
    </div>
  );
}
