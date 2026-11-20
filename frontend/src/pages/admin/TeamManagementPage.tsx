import { useEffect, useState } from 'react';
import { useAdminStore } from '../../stores/adminStore';
import { Plus, Trash2, Loader2, Users } from 'lucide-react';
import * as api from '../../services/api';
import { confirmDialog, toast } from '../../components/common/feedback';
import type { TeamMember } from '../../types/admin';
import { useCapabilities } from '../../hooks/useCapabilities';
import {
  Alert, Badge, Button, Card, EmptyState, Field, IconButton, Input, Modal, PageHeader,
} from '../../components/admin/ui';

export default function TeamManagementPage() {
  const { teams, loadTeams, createTeam, deleteTeam, error, clearError } = useAdminStore();
  const { admin, canManageTeam } = useCapabilities();
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [desc, setDesc] = useState('');
  const [selectedTeam, setSelectedTeam] = useState<number | null>(null);
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [memberUserId, setMemberUserId] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => { loadTeams(); }, [loadTeams]);

  const handleCreate = async () => {
    if (!name.trim()) return;
    await createTeam(name, desc);
    toast.success('团队已创建');
    setName(''); setDesc(''); setShowCreate(false);
  };

  const handleDeleteTeam = async (id: number, teamName: string) => {
    const ok = await confirmDialog({
      title: '删除团队',
      description: `确认删除「${teamName}」？团队空间内的共享资源将失去团队可见性。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    await deleteTeam(id);
    toast.success('团队已删除');
  };

  const handleSelectTeam = async (teamId: number) => {
    setSelectedTeam(teamId);
    setLoading(true);
    try {
      const res = await api.getTeamMembers(teamId);
      setMembers(res.data);
    } catch { /* ignore */ }
    setLoading(false);
  };

  const handleAddMember = async () => {
    if (!selectedTeam || !memberUserId) return;
    await api.addTeamMember(selectedTeam, Number(memberUserId), 'member');
    await handleSelectTeam(selectedTeam);
    setMemberUserId('');
    toast.success('成员已添加');
  };

  const handleRemoveMember = async (userId: number) => {
    if (!selectedTeam) return;
    const ok = await confirmDialog({
      title: '移除成员',
      description: '确认将该用户移出团队？',
      confirmText: '移除',
      danger: true,
    });
    if (!ok) return;
    await api.removeTeamMember(selectedTeam, userId);
    await handleSelectTeam(selectedTeam);
    toast.success('成员已移除');
  };

  const activeTeam = teams.find((t) => t.id === selectedTeam);
  const canManageActive = canManageTeam(activeTeam?.id);

  return (
    <div>
      <PageHeader
        title="团队"
        description="团队用于共享扩展包、数据源、接口与智能体；切换右上角数据上下文即可进入团队空间。"
        actions={<Button variant="primary" onClick={() => setShowCreate(true)}><Plus size={15} /> 创建团队</Button>}
      />

      {error && <Alert onClose={clearError}>{error}</Alert>}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[320px_minmax(0,1fr)]">
        <Card title="团队列表" description={`共 ${teams.length} 个团队`} flush>
          {teams.length === 0 ? (
            <div className="px-5 py-10 text-center text-sm text-gray-400">暂无团队</div>
          ) : (
            <div className="divide-y divide-gray-100">
              {teams.map((t) => (
                <div
                  key={t.id}
                  onClick={() => handleSelectTeam(t.id)}
                  className={`flex cursor-pointer items-center gap-3 px-5 py-3.5 transition-colors ${
                    selectedTeam === t.id ? 'bg-indigo-50/60' : 'hover:bg-gray-50'
                  }`}
                >
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gray-50 text-gray-400">
                    <Users size={15} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-medium text-gray-800">{t.name}</div>
                    <div className="truncate text-xs text-gray-400">{t.description || '暂无描述'}</div>
                  </div>
                  {(admin || canManageTeam(t.id)) && (
                    <IconButton
                      tone="danger"
                      aria-label="删除团队"
                      onClick={(e) => { e.stopPropagation(); handleDeleteTeam(t.id, t.name); }}
                    >
                      <Trash2 size={14} />
                    </IconButton>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title={activeTeam ? `成员 · ${activeTeam.name}` : '成员管理'}
          description={activeTeam ? '通过用户 ID 添加成员，成员可访问团队空间资源' : '选择左侧团队查看成员'}
        >
          {!activeTeam ? (
            <EmptyState icon={<Users size={18} />} title="未选择团队" description="从左侧列表选择一个团队后即可管理成员" />
          ) : (
            <>
              {canManageActive ? (
              <div className="flex items-end gap-2">
                <Field label="用户 ID" className="flex-1">
                  <Input
                    value={memberUserId}
                    onChange={(e) => setMemberUserId(e.target.value)}
                    placeholder="输入要添加的用户 ID"
                  />
                </Field>
                <Button variant="primary" onClick={handleAddMember}><Plus size={15} /> 添加成员</Button>
              </div>
              ) : (
                <div className="rounded-lg border border-dashed border-gray-200 px-4 py-3 text-xs text-gray-500">
                  你是该团队成员，仅团队负责人或团队管理员可管理成员。
                </div>
              )}

              <div className="mt-4 overflow-hidden rounded-lg border border-gray-200">
                <div className="flex items-center gap-2 border-b border-gray-100 bg-gray-50/60 px-3 py-2 text-[11px] font-medium uppercase tracking-wider text-gray-400">
                  <span className="flex-1">成员</span>
                  <span className="w-20">角色</span>
                  <span className="w-8" />
                </div>
                {loading ? (
                  <div className="flex items-center justify-center gap-2 py-8 text-sm text-gray-400">
                    <Loader2 className="animate-spin" size={16} /> 加载中
                  </div>
                ) : members.length === 0 ? (
                  <div className="py-8 text-center text-sm text-gray-400">暂无成员</div>
                ) : (
                  members.map((m) => (
                    <div key={m.id} className="flex items-center gap-2 border-b border-gray-50 px-3 py-2.5 text-sm last:border-0">
                      <span className="flex-1 text-gray-700">用户 {m.user.id}</span>
                      <span className="w-20">
                        <Badge tone={m.role === 'owner' ? 'indigo' : 'gray'}>{m.role}</Badge>
                      </span>
                      {canManageActive && m.role !== 'owner' && (
                        <IconButton tone="danger" aria-label="移除成员" onClick={() => handleRemoveMember(m.user.id)}>
                          <Trash2 size={14} />
                        </IconButton>
                      )}
                    </div>
                  ))
                )}
              </div>
            </>
          )}
        </Card>
      </div>

      {showCreate && (
        <Modal
          title="创建团队"
          description="团队资源仅团队内成员可见"
          onClose={() => setShowCreate(false)}
          width="max-w-md"
          footer={
            <>
              <Button onClick={() => setShowCreate(false)}>取消</Button>
              <Button variant="primary" onClick={handleCreate}>创建</Button>
            </>
          }
        >
          <div className="space-y-4">
            <Field label="团队名称" required>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="如：销售运营团队" />
            </Field>
            <Field label="描述">
              <Input value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="可选" />
            </Field>
          </div>
        </Modal>
      )}
    </div>
  );
}
