import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, Loader2, Puzzle, ShieldCheck, XCircle } from 'lucide-react';
import * as api from '../../services/api';
import { confirmDialog, toast } from '../../components/common/feedback';
import type { ExtensionItem } from '../../types/admin';
import type { Skill } from '../../types';
import {
  Alert, Badge, Button, Card, EmptyState, Modal, PageHeader, Textarea,
} from '../../components/admin/ui';

/**
 * 上架审核中心：管理员统一处理技能包与扩展包的公开上架申请。
 */
export default function ReviewCenterPage() {
  const [skills, setSkills] = useState<Skill[]>([]);
  const [extensions, setExtensions] = useState<ExtensionItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [rejecting, setRejecting] = useState<{ kind: 'skill' | 'extension'; id: string; name: string } | null>(null);
  const [reason, setReason] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [skillRes, extRes] = await Promise.all([
        api.getSkills(undefined, 'pending'),
        api.getExtensions('pending'),
      ]);
      setSkills(skillRes.data || []);
      setExtensions(extRes.data || []);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const approveSkill = async (skill: Skill) => {
    const ok = await confirmDialog({
      title: '通过上架',
      description: `同意「${skill.displayName}」公开上架？通过后所有用户可见。`,
      confirmText: '通过',
    });
    if (!ok) return;
    try {
      await api.reviewSkill(skill.id, true);
      toast.success('已通过');
      load();
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const approveExtension = async (item: ExtensionItem) => {
    const ok = await confirmDialog({
      title: '通过上架',
      description: `同意「${item.extName}」公开上架？通过后其工具对所有用户可用。`,
      confirmText: '通过',
    });
    if (!ok) return;
    try {
      await api.reviewExtension(item.extCode, true);
      toast.success('已通过');
      load();
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const submitReject = async () => {
    if (!rejecting) return;
    try {
      if (rejecting.kind === 'skill') {
        await api.reviewSkill(Number(rejecting.id), false, reason.trim() || undefined);
      } else {
        await api.reviewExtension(rejecting.id, false, reason.trim() || undefined);
      }
      toast.success('已驳回');
      setRejecting(null);
      setReason('');
      load();
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const total = skills.length + extensions.length;

  return (
    <>
      <PageHeader
        title="上架审核"
        description="技能包与扩展包申请公开后在此审核；通过即全平台可见，驳回需填写原因。"
        actions={<Button onClick={load} disabled={loading}>
          {loading ? <Loader2 size={14} className="animate-spin" /> : <ShieldCheck size={14} />} 刷新
        </Button>}
      />

      {error && <Alert onClose={() => setError(null)}>{error}</Alert>}

      {!loading && total === 0 && (
        <EmptyState
          icon={<ShieldCheck size={18} />}
          title="没有待审核的申请"
          description="用户提交上架后会自动出现在这里。"
        />
      )}

      {skills.length > 0 && (
        <Card title={`技能包待审核（${skills.length}）`} description="技能包上架后，所有用户可在技能市场中看到并使用" className="mb-4">
          <div className="divide-y divide-gray-100">
            {skills.map((skill) => (
              <div key={skill.id} className="flex items-start gap-3 py-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gray-50 text-gray-400">
                  <Puzzle size={15} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium text-gray-900">{skill.displayName}</span>
                    <code className="text-[11px] text-gray-400">{skill.name}</code>
                    <Badge tone="amber">待审核</Badge>
                    {skill.visibility === 'team' && <Badge tone="blue">团队</Badge>}
                  </div>
                  <p className="mt-0.5 line-clamp-2 text-xs text-gray-500">{skill.description || '暂无描述'}</p>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button size="sm" onClick={() => approveSkill(skill)}>
                    <CheckCircle2 size={14} /> 通过
                  </Button>
                  <Button size="sm" variant="ghost"
                    onClick={() => { setRejecting({ kind: 'skill', id: String(skill.id), name: skill.displayName }); setReason(''); }}>
                    <XCircle size={14} /> 驳回
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {extensions.length > 0 && (
        <Card title={`扩展包待审核（${extensions.length}）`} description="扩展包含有工具与渲染能力，通过前请确认来源可信" className="mb-4">
          <div className="divide-y divide-gray-100">
            {extensions.map((item) => (
              <div key={item.id} className="flex items-start gap-3 py-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gray-50 text-gray-400">
                  <ShieldCheck size={15} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium text-gray-900">{item.extName}</span>
                    <code className="text-[11px] text-gray-400">{item.extCode} v{item.version}</code>
                    <Badge tone="amber">待审核</Badge>
                    <Badge tone="indigo">{item.packType}</Badge>
                    {item.visibility === 'team' && <Badge tone="blue">团队</Badge>}
                  </div>
                  <p className="mt-0.5 line-clamp-2 text-xs text-gray-500">{item.description || '暂无描述'}</p>
                  <div className="mt-0.5 text-[11px] text-gray-400">
                    {item.tools.length} 个工具 · {item.renderers.length} 个渲染器
                  </div>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button size="sm" onClick={() => approveExtension(item)}>
                    <CheckCircle2 size={14} /> 通过
                  </Button>
                  <Button size="sm" variant="ghost"
                    onClick={() => { setRejecting({ kind: 'extension', id: item.extCode, name: item.extName }); setReason(''); }}>
                    <XCircle size={14} /> 驳回
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {rejecting && (
        <Modal
          title="驳回上架申请"
          description={`「${rejecting.name}」将退回给提交人，并附上驳回原因`}
          width="max-w-lg"
          onClose={() => setRejecting(null)}
          footer={
            <>
              <Button onClick={() => setRejecting(null)}>取消</Button>
              <Button variant="primary" onClick={submitReject}>确认驳回</Button>
            </>
          }
        >
          <Textarea
            rows={4}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="例如：提示词包含未经确认的业务口径，请补充数据来源说明后重新提交"
          />
        </Modal>
      )}
    </>
  );
}
