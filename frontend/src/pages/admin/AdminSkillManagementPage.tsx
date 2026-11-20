import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSkillStore } from '../../stores/skillStore';
import * as api from '../../services/api';
import { confirmDialog, toast } from '../../components/common/feedback';
import { useCapabilities } from '../../hooks/useCapabilities';
import type { Skill } from '../../types';
import { Check, Download, Pencil, Plus, Puzzle, Trash2, Upload } from 'lucide-react';
import {
  Badge, Button, Card, EmptyState, Input, PageHeader,
} from '../../components/admin/ui';

interface ToolOption {
  toolKey: string;
  displayName: string;
  toolKind: string;
}

/** 上架状态徽标 */
function publishBadge(status?: string | null) {
  if (status === 'pending') return <Badge tone="amber">待审核</Badge>;
  if (status === 'approved') return <Badge tone="green">已上架</Badge>;
  if (status === 'rejected') return <Badge tone="red">已驳回</Badge>;
  return <Badge tone="gray">未上架</Badge>;
}

export default function AdminSkillManagementPage() {
  const { canManage } = useCapabilities();
  const { skills, loadSkills } = useSkillStore();
  const navigate = useNavigate();
  const [tools, setTools] = useState<ToolOption[]>([]);
  const [query, setQuery] = useState('');
  const [editingToolsId, setEditingToolsId] = useState<number | null>(null);
  const [selectedTools, setSelectedTools] = useState<string[]>([]);

  useEffect(() => {
    loadSkills();
    api.getExtensionTools()
      .then((res) => setTools(res.data || []))
      .catch(() => setTools([]));
  }, [loadSkills]);

  const handleDelete = async (skillId: number) => {
    const ok = await confirmDialog({
      title: '删除技能包',
      description: '绑定该技能包的智能体将失去对应提示词与工具授权。',
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteSkill(skillId);
      toast.success('技能包已删除');
      loadSkills();
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const handleToolsEdit = (skillId: number, currentTools: string[] | null) => {
    setEditingToolsId(skillId);
    setSelectedTools(currentTools || []);
  };

  const handleToolsSave = async (skillId: number) => {
    try {
      await api.updateToolBindings(skillId, selectedTools);
      toast.success('工具绑定已保存');
    } catch (e) {
      toast.error((e as Error).message);
    }
    setEditingToolsId(null);
    loadSkills();
  };

  const toggleTool = (toolKey: string) => {
    setSelectedTools((prev) => prev.includes(toolKey) ? prev.filter((t) => t !== toolKey) : [...prev, toolKey]);
  };

  const handleImport = async (file: File) => {
    try {
      await api.importSkill(file);
      toast.success('技能包已导入');
      loadSkills();
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const filtered = skills.filter((skill) => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return true;
    return skill.displayName.toLowerCase().includes(keyword)
      || skill.name.toLowerCase().includes(keyword)
      || (skill.description || '').toLowerCase().includes(keyword);
  });

  const handlePublish = async (skill: Skill) => {
    const ok = await confirmDialog({
      title: '申请上架',
      description: `将「${skill.displayName}」提交平台审核，通过后所有用户可见。`,
      confirmText: '提交申请',
    });
    if (!ok) return;
    try {
      await api.publishSkill(skill.id);
      toast.success('已提交上架申请');
      loadSkills();
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const handleWithdraw = async (skill: Skill) => {
    const ok = await confirmDialog({
      title: skill.publishStatus === 'pending' ? '撤回申请' : '下架',
      description: `确认将「${skill.displayName}」退出公开可见？`,
      confirmText: '确认',
      danger: skill.publishStatus === 'approved',
    });
    if (!ok) return;
    try {
      await api.withdrawSkill(skill.id);
      toast.success('已撤出上架');
      loadSkills();
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  return (
    <div>
      <PageHeader
        title="技能包"
        description="技能包 = 提示词 + 资源引用。提示词中可用 {{datasource:code}}、{{api:code}}、{{extension:code}} 占位符，运行时自动展开。"
        actions={
          <>
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索技能包"
              className="h-9 w-44 text-[13px]"
            />
            <label className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50">
              <Upload size={15} /> 导入
              <input type="file" accept=".zip" className="hidden"
                onChange={(e) => { const f = e.target.files?.[0]; if (f) handleImport(f); }} />
            </label>
            <Button variant="primary" onClick={() => navigate('/admin/skills/new')}>
              <Plus size={15} /> 创建技能包
            </Button>
          </>
        }
      />

      {skills.length === 0 ? (
        <EmptyState
          icon={<Puzzle size={18} />}
          title="还没有技能包"
          description="创建技能包并引用业务数据源与接口，让智能体按业务规范工作"
          action={<Button variant="primary" onClick={() => navigate('/admin/skills/new')}><Plus size={15} /> 创建技能包</Button>}
        />
      ) : (
        <div className="space-y-3">
          {filtered.map((skill) => (
            <Card key={skill.id}>
              <div className="flex items-start gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gray-50 text-gray-400">
                  <Puzzle size={16} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="text-sm font-semibold text-gray-900">{skill.displayName}</h3>
                    <code className="text-[11px] text-gray-400">{skill.name}</code>
                    {skill.packSource && <Badge>{skill.packSource}</Badge>}
                    {skill.triggerMode && <Badge tone="blue">{skill.triggerMode}</Badge>}
                    {publishBadge(skill.publishStatus)}
                    {skill.visibility === 'team' && <Badge tone="blue">团队</Badge>}
                    {skill.skillVersion && <span className="text-[11px] text-gray-400">v{skill.skillVersion}</span>}
                  </div>
                  <p className="mt-1 text-xs leading-relaxed text-gray-500">{skill.description || '暂无描述'}</p>
                  {skill.publishStatus === 'rejected' && skill.rejectReason && (
                    <div className="mt-1 text-[11px] text-amber-600">审核未通过：{skill.rejectReason}</div>
                  )}

                  <div className="mt-2 flex flex-wrap items-center gap-1.5">
                    {(skill.requiredTools || []).map((t) => (
                      <code key={t} className="rounded-md bg-gray-50 px-1.5 py-0.5 text-[11px] text-gray-600">{t}</code>
                    ))}
                    <button
                      onClick={() => handleToolsEdit(skill.id, skill.requiredTools)}
                      className="rounded-md px-1.5 py-0.5 text-[11px] font-medium text-indigo-600 hover:bg-indigo-50 cursor-pointer"
                    >
                      + 工具绑定
                    </button>
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <a href={api.getSkillExportUrl(skill.id)} title="导出技能包"
                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700">
                    <Download size={14} />
                  </a>
                  {canManage(skill.ownerId, skill.teamId) && skill.ownerId != null
                    && (skill.publishStatus === 'draft' || !skill.publishStatus) && (
                    <Button size="sm" onClick={() => handlePublish(skill)}>申请上架</Button>
                  )}
                  {canManage(skill.ownerId, skill.teamId) && skill.ownerId != null
                    && ['pending', 'approved', 'rejected'].includes(skill.publishStatus || '') && (
                    <Button size="sm" variant="ghost" onClick={() => handleWithdraw(skill)}>
                      {skill.publishStatus === 'pending' ? '撤回申请' : '下架'}
                    </Button>
                  )}
                  {canManage(skill.ownerId, skill.teamId) && (
                    <>
                      <button onClick={() => navigate(`/admin/skills/${skill.id}/edit`)} title="编辑"
                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700 cursor-pointer">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => handleDelete(skill.id)} title="删除"
                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 cursor-pointer">
                        <Trash2 size={14} />
                      </button>
                    </>
                  )}
                </div>
              </div>

              {editingToolsId === skill.id && (
                <div className="mt-4 rounded-lg border border-gray-200 bg-gray-50/50 p-4">
                  <div className="mb-2.5 text-xs font-medium text-gray-600">选择扩展包工具</div>
                  <div className="mb-3 flex flex-wrap gap-1.5">
                    {tools.map((t) => {
                      const active = selectedTools.includes(t.toolKey);
                      return (
                        <button
                          key={t.toolKey}
                          onClick={() => toggleTool(t.toolKey)}
                          className={`inline-flex items-center gap-1 rounded-md border px-2 py-1 text-[11px] transition-colors cursor-pointer ${
                            active
                              ? 'border-indigo-200 bg-indigo-50 text-indigo-700'
                              : 'border-gray-200 bg-white text-gray-600 hover:border-gray-300'
                          }`}
                        >
                          {active && <Check size={10} />}
                          {t.toolKey}
                        </button>
                      );
                    })}
                    {tools.length === 0 && <span className="text-xs text-gray-400">暂无可用工具，请先安装扩展包</span>}
                  </div>
                  <div className="flex gap-2">
                    <Button size="sm" variant="primary" onClick={() => handleToolsSave(skill.id)}>保存</Button>
                    <Button size="sm" onClick={() => setEditingToolsId(null)}>取消</Button>
                  </div>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
