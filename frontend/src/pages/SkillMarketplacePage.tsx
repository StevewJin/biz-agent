import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useSkillStore } from '../stores/skillStore';
import { useChatStore } from '../stores/chatStore';
import { ArrowLeft, BarChart3, FileText, Zap, Loader2, Check, Settings } from 'lucide-react';
import type { Skill } from '../types';

const ICON_MAP: Record<string, React.ComponentType<{ size?: number; className?: string }>> = {
  'bar-chart-3': BarChart3,
  'file-text': FileText,
  'zap': Zap,
};

function SkillIcon({ icon }: { icon: string }) {
  const Icon = ICON_MAP[icon] || Zap;
  return <Icon size={24} />;
}

export default function SkillMarketplacePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const conversationId = searchParams.get('conversationId')
    ? Number(searchParams.get('conversationId'))
    : undefined;

  const { skills, isLoading, error, loadSkills, toggleSkill } = useSkillStore();
  const currentConversation = useChatStore((s) => s.currentConversation);
  const [togglingIds, setTogglingIds] = useState<Set<number>>(new Set());

  const activeConvId = conversationId || currentConversation?.id;

  useEffect(() => {
    loadSkills(activeConvId);
  }, [loadSkills, activeConvId]);

  const handleToggle = async (skillId: number) => {
    if (!activeConvId) {
      navigate('/chat');
      return;
    }
    setTogglingIds((prev) => new Set(prev).add(skillId));
    await toggleSkill(skillId, activeConvId);
    setTogglingIds((prev) => {
      const next = new Set(prev);
      next.delete(skillId);
      return next;
    });
  };

  const handleBack = () => {
    navigate(activeConvId ? `/chat/${activeConvId}` : '/chat');
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 导航栏 */}
      <header className="h-14 bg-white border-b border-gray-200 flex items-center px-6 sticky top-0 z-10">
        <button
          onClick={handleBack}
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900 cursor-pointer"
        >
          <ArrowLeft size={18} />
          <span className="text-sm font-medium">返回聊天</span>
        </button>
        <h1 className="text-lg font-bold text-purple-600 ml-6">技能市场</h1>
      </header>

      <main className="max-w-4xl mx-auto p-6">
        {/* 标题区 */}
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-gray-800 mb-2">
            探索业务技能
          </h2>
          <p className="text-gray-500">
            为你的 BizAgent 启用专业技能，扩展 AI 助手的能力边界
          </p>
        </div>

        {/* 错误提示 */}
        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {error}
          </div>
        )}

        {/* 加载中 */}
        {isLoading && (
          <div className="flex items-center justify-center py-20">
            <Loader2 size={32} className="text-purple-500 animate-spin" />
            <span className="ml-3 text-gray-500">加载技能列表...</span>
          </div>
        )}

        {/* 技能卡片网格 */}
        {!isLoading && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {skills.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                onToggle={() => handleToggle(skill.id)}
                isToggling={togglingIds.has(skill.id)}
                hasActiveConversation={!!activeConvId}
              />
            ))}
          </div>
        )}

        {/* 空状态 */}
        {!isLoading && skills.length === 0 && !error && (
          <div className="text-center py-20">
            <Zap size={48} className="text-gray-300 mx-auto mb-4" />
            <p className="text-gray-500">暂无可用技能</p>
          </div>
        )}
      </main>
    </div>
  );
}

function SkillCard({
  skill,
  onToggle,
  isToggling,
  hasActiveConversation,
}: {
  skill: Skill;
  onToggle: () => void;
  isToggling: boolean;
  hasActiveConversation: boolean;
}) {
  const navigate = useNavigate();

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5 hover:border-purple-200 hover:shadow-sm transition">
      <div className="flex items-start gap-4">
        <div className={`w-12 h-12 rounded-lg flex items-center justify-center shrink-0 ${
          skill.active
            ? 'bg-purple-100 text-purple-600'
            : 'bg-gray-100 text-gray-500'
        }`}>
          <SkillIcon icon={skill.icon} />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="font-semibold text-gray-800">{skill.displayName}</h3>
            {skill.active && (
              <span className="flex items-center gap-0.5 text-xs text-green-600 bg-green-50 px-1.5 py-0.5 rounded-full">
                <Check size={10} />
                已激活
              </span>
            )}
          </div>
          <p className="text-sm text-gray-500 mb-3">{skill.description}</p>

          {skill.requiredTools && skill.requiredTools.length > 0 && (
            <div className="flex flex-wrap gap-1 mb-3">
              {skill.requiredTools.map((tool) => (
                <span
                  key={tool}
                  className="text-xs bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded"
                >
                  {tool}
                </span>
              ))}
            </div>
          )}

          <div className="flex items-center gap-2">
            <button
              onClick={onToggle}
              disabled={isToggling || !hasActiveConversation}
              className={`text-sm px-3 py-1 rounded-lg cursor-pointer transition ${
                skill.active
                  ? 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  : 'bg-purple-600 text-white hover:bg-purple-700'
              } disabled:opacity-50 disabled:cursor-not-allowed`}
            >
              {isToggling ? (
                <Loader2 size={14} className="animate-spin inline" />
              ) : skill.active ? (
                '停用'
              ) : (
                '激活'
              )}
            </button>

            {skill.configSchema && Object.keys(skill.configSchema).length > 0 && (
              <button
                onClick={() => navigate(`/skills/${skill.id}/config`)}
                className="text-sm text-gray-500 hover:text-gray-700 flex items-center gap-1 cursor-pointer"
                title="配置技能"
              >
                <Settings size={14} />
                配置
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
