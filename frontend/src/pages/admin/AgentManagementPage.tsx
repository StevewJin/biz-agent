import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAdminStore } from '../../stores/adminStore';
import { useAuthStore } from '../../stores/authStore';
import { useCapabilities } from '../../hooks/useCapabilities';
import { Bot, Copy, ExternalLink, Plus, Settings, Trash2 } from 'lucide-react';
import { confirmDialog, toast } from '../../components/common/feedback';
import {
  Badge, Button, Card, EmptyState, Field, IconButton, Input, Modal, PageHeader, Textarea,
} from '../../components/admin/ui';

export default function AgentManagementPage() {
  const navigate = useNavigate();
  const { agents, loadAgents, createAgent, deleteAgent, agentsLoading } = useAdminStore();
  const { dataContext } = useAuthStore();
  const { inTeam, canManage } = useCapabilities();
  const currentTeamId = dataContext.type === 'team' ? dataContext.teamId : undefined;
  const canCreate = inTeam(currentTeamId);
  const [showCreate, setShowCreate] = useState(false);
  const [query, setQuery] = useState('');
  const [form, setForm] = useState({ name: '', description: '', systemPrompt: '' });

  useEffect(() => {
    loadAgents(dataContext.type === 'team' ? dataContext.teamId : undefined);
  }, [loadAgents, dataContext]);

  const handleCreate = async () => {
    if (!form.name.trim()) return;
    await createAgent({ ...form, teamId: dataContext.type === 'team' ? dataContext.teamId : null });
    toast.success('智能体已创建');
    setShowCreate(false);
    setForm({ name: '', description: '', systemPrompt: '' });
  };

  const handleDelete = async (id: number, name: string) => {
    const ok = await confirmDialog({
      title: '删除智能体',
      description: `确认删除「${name}」？其分享链接会立即失效。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    await deleteAgent(id);
    toast.success('智能体已删除');
  };

  const filtered = agents.filter((agent) => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return true;
    return agent.name.toLowerCase().includes(keyword)
      || (agent.description || '').toLowerCase().includes(keyword);
  });

  const copyShareUrl = (shareToken: string) => {
    navigator.clipboard.writeText(`${window.location.origin}/agent/${shareToken}`);
  };

  return (
    <div>
      <PageHeader
        title="智能体"
        description="智能体绑定 LLM 供应商、技能包与扩展包，对外可通过分享链接匿名访问。"
        actions={
          <>
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索智能体"
              className="h-9 w-44 text-[13px]"
            />
            {canCreate && (
              <Button variant="primary" onClick={() => setShowCreate(true)}><Plus size={15} /> 创建智能体</Button>
            )}
          </>
        }
      />

      {agentsLoading ? (
        <div className="py-16 text-center text-sm text-gray-400">加载中...</div>
      ) : agents.length === 0 ? (
        <EmptyState
          icon={<Bot size={18} />}
          title="还没有智能体"
          description="创建一个智能体，绑定技能包与扩展包后即可对外提供业务能力"
          action={canCreate
            ? <Button variant="primary" onClick={() => setShowCreate(true)}><Plus size={15} /> 创建智能体</Button>
            : undefined}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {filtered.map((a) => (
            <Card key={a.id} className="transition-shadow hover:shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gray-900 text-white">
                  <Bot size={18} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="truncate text-sm font-semibold text-gray-900">{a.name}</h3>
                    <Badge tone={a.scope === 'team' ? 'indigo' : 'gray'}>
                      {a.scope === 'team' ? '团队' : '个人'}
                    </Badge>
                  </div>
                  <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-gray-500">
                    {a.description || '暂无描述'}
                  </p>
                  {a.shareToken && (
                    <div className="mt-2 flex items-center gap-1.5 text-xs text-gray-400">
                      <code className="truncate rounded bg-gray-50 px-1.5 py-0.5">/agent/{a.shareToken.slice(0, 8)}…</code>
                      <button onClick={() => copyShareUrl(a.shareToken!)} className="hover:text-gray-700 cursor-pointer" aria-label="复制链接">
                        <Copy size={12} />
                      </button>
                      <button onClick={() => window.open(`/agent/${a.shareToken}`, '_blank')} className="hover:text-gray-700 cursor-pointer" aria-label="打开">
                        <ExternalLink size={12} />
                      </button>
                    </div>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <IconButton aria-label="设置" onClick={() => navigate(`/admin/agents/${a.id}`)}>
                    <Settings size={14} />
                  </IconButton>
                  {canManage(a.owner?.id, a.team?.id) && (
                    <IconButton tone="danger" aria-label="删除" onClick={() => handleDelete(a.id, a.name)}>
                      <Trash2 size={14} />
                    </IconButton>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {showCreate && (
        <Modal
          title="创建智能体"
          description="创建后可在详情页绑定技能包、扩展包与分享链接"
          onClose={() => setShowCreate(false)}
          footer={
            <>
              <Button onClick={() => setShowCreate(false)}>取消</Button>
              <Button variant="primary" onClick={handleCreate}>创建</Button>
            </>
          }
        >
          <div className="space-y-4">
            <Field label="名称" required>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="如：销售数据分析助手" />
            </Field>
            <Field label="描述">
              <Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="一句话说明智能体用途" />
            </Field>
            <Field label="系统提示词" hint="留空则使用平台默认提示词">
              <Textarea rows={5} value={form.systemPrompt} onChange={(e) => setForm({ ...form, systemPrompt: e.target.value })} placeholder="你是一名业务分析助手…" />
            </Field>
          </div>
        </Modal>
      )}
    </div>
  );
}
