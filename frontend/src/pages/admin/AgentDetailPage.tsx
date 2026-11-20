import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Copy, ExternalLink, Key, Package, Plus, RefreshCw, Trash2 } from 'lucide-react';
import * as api from '../../services/api';
import { useSkillStore } from '../../stores/skillStore';
import { useAdminStore } from '../../stores/adminStore';
import type { AgentItem, AgentSkillItem, ExtensionItem } from '../../types/admin';
import {
  Badge, Button, Card, Field, IconButton, Input, PageHeader, Select, Textarea,
} from '../../components/admin/ui';

export default function AgentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { skills, loadSkills } = useSkillStore();
  const { providers, loadProviders } = useAdminStore();
  const [agent, setAgent] = useState<AgentItem | null>(null);
  const [agentSkills, setAgentSkills] = useState<AgentSkillItem[]>([]);
  const [agentExtensions, setAgentExtensions] = useState<{ extCode: string; extName: string; version: string; status: string }[]>([]);
  const [extensions, setExtensions] = useState<ExtensionItem[]>([]);
  const [form, setForm] = useState({ name: '', description: '', systemPrompt: '', providerId: '' });

  useEffect(() => { loadSkills(); loadProviders(); }, [loadSkills, loadProviders]);

  useEffect(() => {
    if (!id) return;
    api.getAgent(Number(id)).then((r) => {
      setAgent(r.data);
      setForm({
        name: r.data.name,
        description: r.data.description || '',
        systemPrompt: r.data.systemPrompt || '',
        providerId: r.data.provider?.id ? String(r.data.provider.id) : '',
      });
    });
    api.getAgentSkills(Number(id)).then((r) => setAgentSkills(r.data));
    api.getAgentExtensions(Number(id)).then((r) => setAgentExtensions(r.data)).catch(() => setAgentExtensions([]));
    api.getExtensions()
      .then((r) => setExtensions((r.data || []).filter((e) => e.status === 'enabled')))
      .catch(() => setExtensions([]));
  }, [id]);

  const handleUpdate = async () => {
    if (!id) return;
    await api.updateAgent(Number(id), { ...form, providerId: form.providerId ? Number(form.providerId) : null });
    const r = await api.getAgent(Number(id));
    setAgent(r.data);
  };

  const handleBindSkill = async (skillId: number) => {
    if (!id) return;
    await api.bindAgentSkill(Number(id), skillId);
    const r = await api.getAgentSkills(Number(id));
    setAgentSkills(r.data);
  };

  const handleUnbindSkill = async (skillId: number) => {
    if (!id) return;
    await api.unbindAgentSkill(Number(id), skillId);
    setAgentSkills((prev) => prev.filter((s) => s.skill.id !== skillId));
  };

  const handleBindExtension = async (extCode: string) => {
    if (!id) return;
    await api.bindAgentExtension(Number(id), extCode);
    const r = await api.getAgentExtensions(Number(id));
    setAgentExtensions(r.data);
  };

  const handleUnbindExtension = async (extCode: string) => {
    if (!id) return;
    await api.unbindAgentExtension(Number(id), extCode);
    setAgentExtensions((prev) => prev.filter((e) => e.extCode !== extCode));
  };

  const handleGenerateAesKey = async () => {
    if (!id) return;
    const r = await api.generateAesKey(Number(id));
    setAgent((prev) => prev ? { ...prev, aesKey: r.data.aesKey } : null);
  };

  const handleRegenerateToken = async () => {
    if (!id) return;
    const r = await api.regenerateShareToken(Number(id));
    setAgent((prev) => prev ? { ...prev, shareToken: r.data.shareToken } : null);
  };

  if (!agent) return <div className="py-16 text-center text-sm text-gray-400">加载中...</div>;

  const boundSkillIds = new Set(agentSkills.map((s) => s.skill.id));
  const shareUrl = agent.shareToken ? `${window.location.origin}/agent/${agent.shareToken}` : '';

  return (
    <div>
      <button
        onClick={() => navigate('/admin/agents')}
        className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-gray-500 transition-colors hover:text-gray-800 cursor-pointer"
      >
        <ArrowLeft size={15} /> 返回智能体列表
      </button>

      <PageHeader
        title={agent.name}
        description={agent.description || '配置智能体的提示词、模型、技能包与扩展包'}
        actions={<Button variant="primary" onClick={handleUpdate}>保存修改</Button>}
      />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <Card title="基本信息">
          <div className="space-y-4">
            <Field label="名称">
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </Field>
            <Field label="描述">
              <Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </Field>
            <Field label="系统提示词" hint="留空则使用平台默认提示词">
              <Textarea rows={5} value={form.systemPrompt} onChange={(e) => setForm({ ...form, systemPrompt: e.target.value })} />
            </Field>
            <Field label="LLM 供应商" hint="未选择时使用平台默认供应商">
              <Select value={form.providerId} onChange={(e) => setForm({ ...form, providerId: e.target.value })}>
                <option value="">平台默认</option>
                {providers.map((p) => <option key={p.id} value={p.id}>{p.name}（{p.providerType}）</option>)}
              </Select>
            </Field>
          </div>
        </Card>

        <Card title="分享与密钥" description="分享链接可匿名访问该智能体">
          <div className="space-y-5">
            <div>
              <div className="mb-1.5 text-xs font-medium text-gray-600">公开链接</div>
              {shareUrl ? (
                <div className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2">
                  <code className="min-w-0 flex-1 truncate text-[11px] text-gray-600">{shareUrl}</code>
                  <IconButton aria-label="复制" onClick={() => navigator.clipboard.writeText(shareUrl)}><Copy size={14} /></IconButton>
                  <a href={`/agent/${agent.shareToken}`} target="_blank" rel="noreferrer"
                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700">
                    <ExternalLink size={14} />
                  </a>
                </div>
              ) : (
                <div className="text-xs text-gray-400">尚未生成分享链接</div>
              )}
              <Button size="sm" className="mt-2" onClick={handleRegenerateToken}>
                <RefreshCw size={13} /> {agent.shareToken ? '重新生成链接' : '生成分享链接'}
              </Button>
            </div>

            <div>
              <div className="mb-1.5 text-xs font-medium text-gray-600">AES 密钥（外部系统用户名解密）</div>
              {agent.aesKey ? (
                <code className="block break-all rounded-lg bg-gray-50 px-3 py-2 text-[11px] text-gray-600">{agent.aesKey}</code>
              ) : (
                <div className="text-xs text-gray-400">尚未生成</div>
              )}
              <Button size="sm" className="mt-2" onClick={handleGenerateAesKey}>
                <Key size={13} /> 生成密钥
              </Button>
            </div>
          </div>
        </Card>

        <Card
          className="lg:col-span-2"
          title="绑定的扩展包"
          description="绑定的扩展包工具会直接注入该智能体的工具白名单"
        >
          <div className="mb-3 flex flex-wrap gap-1.5">
            {extensions.filter((ext) => !agentExtensions.some((e) => e.extCode === ext.extCode)).map((ext) => (
              <button
                key={ext.extCode}
                onClick={() => handleBindExtension(ext.extCode)}
                className="inline-flex items-center gap-1 rounded-md border border-gray-200 bg-white px-2 py-1 text-[11px] text-gray-600 transition-colors hover:border-gray-300 hover:text-gray-900 cursor-pointer"
              >
                <Plus size={10} /> {ext.extName}
              </button>
            ))}
            {extensions.length === 0 && <span className="text-xs text-gray-400">暂无已启用的扩展包</span>}
          </div>

          {agentExtensions.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-200 px-4 py-8 text-center text-xs text-gray-400">
              尚未绑定扩展包
            </div>
          ) : (
            <div className="overflow-hidden rounded-lg border border-gray-200">
              {agentExtensions.map((ext) => (
                <div key={ext.extCode} className="flex items-center gap-3 border-b border-gray-50 px-3 py-2.5 text-sm last:border-0">
                  <Package size={14} className="text-gray-400" />
                  <span className="font-medium text-gray-800">{ext.extName}</span>
                  <code className="text-[11px] text-gray-400">{ext.extCode} v{ext.version}</code>
                  <Badge tone={ext.status === 'enabled' ? 'green' : 'gray'}>{ext.status}</Badge>
                  <IconButton tone="danger" className="ml-auto" aria-label="解绑" onClick={() => handleUnbindExtension(ext.extCode)}>
                    <Trash2 size={14} />
                  </IconButton>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          className="lg:col-span-2"
          title="绑定的技能包"
          description="技能包决定提示词、资源引用与可用工具"
        >
          <div className="mb-3 flex flex-wrap gap-1.5">
            {skills.filter((s) => !boundSkillIds.has(s.id)).map((s) => (
              <button
                key={s.id}
                onClick={() => handleBindSkill(s.id)}
                className="inline-flex items-center gap-1 rounded-md border border-gray-200 bg-white px-2 py-1 text-[11px] text-gray-600 transition-colors hover:border-gray-300 hover:text-gray-900 cursor-pointer"
              >
                <Plus size={10} /> {s.displayName}
              </button>
            ))}
            {skills.length === 0 && <span className="text-xs text-gray-400">暂无技能包</span>}
          </div>

          {agentSkills.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-200 px-4 py-8 text-center text-xs text-gray-400">
              尚未绑定技能包
            </div>
          ) : (
            <div className="overflow-hidden rounded-lg border border-gray-200">
              {agentSkills.map((as) => (
                <div key={as.id} className="flex items-center gap-3 border-b border-gray-50 px-3 py-2.5 text-sm last:border-0">
                  <span className="font-medium text-gray-800">{as.skill.displayName}</span>
                  <code className="text-[11px] text-gray-400">{as.skill.name}</code>
                  <IconButton tone="danger" className="ml-auto" aria-label="解绑" onClick={() => handleUnbindSkill(as.skill.id)}>
                    <Trash2 size={14} />
                  </IconButton>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
