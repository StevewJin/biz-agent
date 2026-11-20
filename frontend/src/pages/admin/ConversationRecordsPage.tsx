import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  History, Search, RefreshCw, Trash2, Eye, Loader2, MessageSquare, Users, User, Building2,
} from 'lucide-react';
import * as api from '../../services/api';
import { useAdminStore } from '../../stores/adminStore';
import { confirmDialog, toast } from '../../components/common/feedback';
import type {
  ConversationRecordDetail, ConversationRecordItem, ConversationRecordScopes,
} from '../../types/admin';
import {
  Alert, Badge, Button, Card, EmptyState, Field, IconButton, Input, Modal, PageHeader, Select,
} from '../../components/admin/ui';

const PAGE_SIZE = 20;

function formatTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

/** 表格内用短时间，避免窄屏把操作列挤出可视区 */
function formatShortTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function statusTone(status: string): 'green' | 'amber' | 'gray' {
  if (status === 'active') return 'green';
  if (status === 'archived') return 'amber';
  return 'gray';
}

/**
 * 会话记录：按「个人 / 团队 / 全部」三级范围查看与清理历史会话。
 * 可见范围由后端按角色判定，前端只做入口收敛。
 */
export default function ConversationRecordsPage() {
  const navigate = useNavigate();
  const { teams, loadTeams } = useAdminStore();

  const [scopes, setScopes] = useState<ConversationRecordScopes>({ all: false, managedTeamIds: [] });
  const [scopesReady, setScopesReady] = useState(false);
  const [scope, setScope] = useState<'mine' | 'team' | 'all'>('mine');
  const [teamId, setTeamId] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [items, setItems] = useState<ConversationRecordItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [detail, setDetail] = useState<ConversationRecordDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const manageableTeams = useMemo(
    () => teams.filter((t) => scopes.all || scopes.managedTeamIds.includes(t.id)),
    [teams, scopes],
  );

  useEffect(() => { loadTeams(); }, [loadTeams]);

  useEffect(() => {
    api.getConversationRecordScopes()
      .then((res) => {
        setScopes(res.data);
        if (res.data.all) {
          setScope('all');
        } else if (res.data.managedTeamIds.length > 0) {
          setScope('team');
          setTeamId(res.data.managedTeamIds[0]);
        }
      })
      .catch(() => undefined)
      .finally(() => setScopesReady(true));
  }, []);

  const fetchRecords = useCallback(async (targetPage = 0) => {
    // 等权限范围就绪再请求，避免先用错范围查一次造成空态闪烁
    if (!scopesReady) return;
    if (scope === 'team' && !teamId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.listConversationRecords({
        scope,
        teamId: scope === 'team' ? teamId : undefined,
        keyword: keyword.trim() || undefined,
        status: status || undefined,
        page: targetPage,
        size: PAGE_SIZE,
      });
      setItems(res.data.content);
      setTotal(res.data.totalElements);
      setPage(res.data.number);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
      setItems([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [scope, teamId, keyword, status, scopesReady]);

  useEffect(() => { fetchRecords(0); }, [fetchRecords]);

  const handleView = async (item: ConversationRecordItem) => {
    setDetailLoading(true);
    try {
      const res = await api.getConversationRecord(item.id);
      setDetail(res.data);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleDelete = async (item: ConversationRecordItem) => {
    const ok = await confirmDialog({
      title: '删除会话记录',
      description: `确认删除「${item.title}」？删除后该会话不再出现在对话列表中。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteConversationRecord(item.id);
      toast.success('会话已删除');
      fetchRecords(page);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '删除失败');
    }
  };

  const scopeLabel = scope === 'all' ? '全平台' : scope === 'team' ? '团队' : '个人';
  // 权限范围未就绪或首次加载中时展示加载态，避免闪出「暂无记录」空态
  const initializing = !scopesReady || (loading && items.length === 0);

  return (
    <>
      <PageHeader
        title="会话记录"
        description="按角色查看历史会话：个人仅本人、团队管理员看本团队、系统管理员可看全平台。"
        actions={(
          <>
            <Button size="sm" onClick={() => fetchRecords(page)} disabled={loading}>
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> 刷新
            </Button>
            <Button size="sm" variant="primary" onClick={() => navigate('/chat')}>
              <MessageSquare size={14} /> 测试对话
            </Button>
          </>
        )}
      />

      {error && <Alert onClose={() => setError(null)}>{error}</Alert>}

      <div className="mb-4 grid gap-3 rounded-xl border border-gray-200 bg-white p-4 md:grid-cols-4">
        <Field label="数据范围">
          <Select
            value={scope}
            onChange={(e) => {
              const next = e.target.value as 'mine' | 'team' | 'all';
              setScope(next);
              if (next === 'team' && !teamId && manageableTeams[0]) setTeamId(manageableTeams[0].id);
            }}
          >
            <option value="mine">个人（我的会话）</option>
            {manageableTeams.length > 0 && <option value="team">团队</option>}
            {scopes.all && <option value="all">全平台</option>}
          </Select>
        </Field>

        <Field label="团队">
          <Select
            value={teamId ?? ''}
            disabled={scope !== 'team'}
            onChange={(e) => setTeamId(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">请选择团队</option>
            {manageableTeams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </Select>
        </Field>

        <Field label="状态">
          <Select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">全部状态</option>
            <option value="active">进行中</option>
            <option value="archived">已归档</option>
          </Select>
        </Field>

        <Field label="搜索标题">
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <Input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="输入标题关键词"
              className="pl-8"
            />
          </div>
        </Field>
      </div>

      <Card flush>
        <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3">
          <div className="text-xs text-gray-500">
            共 <span className="font-medium text-gray-800">{total}</span> 条 · 当前范围
            <span className="ml-1 font-medium text-gray-800">{scopeLabel}</span>
          </div>
          <div className="text-xs text-gray-400">
            第 {total === 0 ? 0 : page + 1} / {Math.max(1, Math.ceil(total / PAGE_SIZE))} 页
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full table-fixed text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/60 text-left text-xs text-gray-500">
                <th className="w-[34%] px-4 py-2.5 font-medium">会话</th>
                <th className="w-[128px] px-4 py-2.5 font-medium">归属</th>
                <th className="hidden w-[104px] px-4 py-2.5 font-medium lg:table-cell">智能体</th>
                <th className="w-[52px] px-4 py-2.5 font-medium">消息</th>
                <th className="w-[72px] px-4 py-2.5 font-medium">状态</th>
                <th className="w-[104px] px-4 py-2.5 font-medium">最近更新</th>
                <th className="w-[76px] px-4 py-2.5 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/60">
                  <td className="max-w-0 px-4 py-3">
                    <button
                      onClick={() => handleView(item)}
                      className="block max-w-full truncate text-left font-medium text-gray-800 hover:text-indigo-600 cursor-pointer"
                      title={item.title}
                    >
                      {item.title}
                    </button>
                    <div className="mt-0.5 truncate text-[11px] text-gray-400">{item.model}</div>
                  </td>
                  <td className="px-4 py-3">
                    {item.teamId ? (
                      <div className="flex items-center gap-1.5 text-gray-700">
                        <Users size={13} className="shrink-0 text-indigo-500" />
                        <span className="truncate">{item.teamName}</span>
                      </div>
                    ) : (
                      <div className="flex items-center gap-1.5 text-gray-600">
                        <User size={13} className="shrink-0 text-gray-400" />
                        <span className="truncate">{item.userName || '—'}</span>
                      </div>
                    )}
                    <div className="mt-0.5 truncate text-[11px] text-gray-400">
                      {item.teamId ? `创建人 ${item.userName || '—'}` : '个人空间'}
                    </div>
                  </td>
                  <td className="hidden truncate px-4 py-3 text-gray-600 lg:table-cell">{item.agentName || '—'}</td>
                  <td className="px-4 py-3 text-gray-600">{item.messageCount}</td>
                  <td className="px-4 py-3">
                    <Badge tone={statusTone(item.status)}>
                      {item.status === 'active' ? '进行中' : item.status === 'archived' ? '已归档' : item.status}
                    </Badge>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-xs text-gray-500">{formatShortTime(item.updatedAt)}</td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-0.5">
                      <IconButton title="查看" onClick={() => handleView(item)}><Eye size={15} /></IconButton>
                      {item.manageable && (
                        <IconButton tone="danger" title="删除" onClick={() => handleDelete(item)}>
                          <Trash2 size={15} />
                        </IconButton>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {initializing && (
          <div className="flex items-center justify-center gap-2 py-10 text-sm text-gray-400">
            <Loader2 size={15} className="animate-spin" /> 加载中…
          </div>
        )}
        {!initializing && !loading && items.length === 0 && (
          <div className="p-5">
            <EmptyState
              icon={<History size={18} />}
              title="暂无会话记录"
              description="当前范围内还没有对话。可以先去「测试对话」发起一次会话，再回到这里查看。"
              action={<Button size="sm" variant="primary" onClick={() => navigate('/chat')}>去测试对话</Button>}
            />
          </div>
        )}
        {loading && items.length > 0 && (
          <div className="flex items-center justify-center gap-2 py-4 text-xs text-gray-400">
            <Loader2 size={14} className="animate-spin" /> 加载中…
          </div>
        )}

        <div className="flex items-center justify-between border-t border-gray-100 px-5 py-3">
          <div className="text-xs text-gray-400">每页 {PAGE_SIZE} 条</div>
          <div className="flex gap-2">
            <Button size="sm" disabled={page <= 0 || loading} onClick={() => fetchRecords(page - 1)}>上一页</Button>
            <Button size="sm" disabled={(page + 1) * PAGE_SIZE >= total || loading} onClick={() => fetchRecords(page + 1)}>下一页</Button>
          </div>
        </div>
      </Card>

      {(detail || detailLoading) && (
        <Modal
          title={detail?.conversation.title || '会话详情'}
          description={detail
            ? `${detail.conversation.teamName ? `团队 · ${detail.conversation.teamName}` : '个人空间'} · ${detail.messages.length} 条消息 · ${formatTime(detail.conversation.createdAt)}`
            : '加载中…'}
          width="max-w-3xl"
          onClose={() => setDetail(null)}
          footer={<Button onClick={() => setDetail(null)}>关闭</Button>}
        >
          {detailLoading && (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-gray-400">
              <Loader2 size={15} className="animate-spin" /> 加载中…
            </div>
          )}
          {!detailLoading && detail && (
            <div className="space-y-3">
              {detail.conversation.agentName && (
                <div className="flex items-center gap-1.5 text-xs text-gray-500">
                  <Building2 size={13} /> 智能体：{detail.conversation.agentName}
                </div>
              )}
              {detail.messages.length === 0 && (
                <div className="rounded-lg border border-dashed border-gray-200 py-10 text-center text-sm text-gray-400">
                  该会话还没有消息
                </div>
              )}
              {detail.messages.map((message) => (
                <div key={message.id} className="rounded-lg border border-gray-200 bg-white p-3.5">
                  <div className="mb-1.5 flex items-center gap-2">
                    <Badge tone={message.role === 'user' ? 'blue' : 'indigo'}>
                      {message.role === 'user' ? '用户' : message.role === 'assistant' ? '助手' : message.role}
                    </Badge>
                    <span className="text-[11px] text-gray-400">{formatTime(message.createdAt)}</span>
                  </div>
                  {message.reasoningContent && (
                    <div className="mb-2 whitespace-pre-wrap rounded-md bg-gray-50 px-2.5 py-2 text-[12px] leading-relaxed text-gray-500">
                      {message.reasoningContent}
                    </div>
                  )}
                  <div className="prose prose-sm max-w-none break-words text-[13px] leading-relaxed text-gray-800 prose-p:my-1 prose-pre:bg-gray-800 prose-pre:text-gray-100 prose-code:rounded prose-code:bg-gray-100 prose-code:px-1 prose-code:text-indigo-600">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content || '（空）'}</ReactMarkdown>
                  </div>
                  {message.thinkingChain && message.thinkingChain.length > 0 && (
                    <details className="mt-2 rounded-md bg-gray-50 px-2.5 py-2 text-[12px] text-gray-500">
                      <summary className="cursor-pointer select-none">
                        思维链 / 工具调用（{message.thinkingChain.length}）
                      </summary>
                      <div className="mt-2 space-y-1.5">
                        {message.thinkingChain.map((entry) => (
                          <div key={entry.id} className="border-l-2 border-gray-200 pl-2">
                            <span className="mr-1.5 font-medium text-gray-600">{entry.type}</span>
                            {entry.toolName && <span className="mr-1.5 text-indigo-600">{entry.toolName}</span>}
                            <span className="whitespace-pre-wrap break-words">{entry.content}</span>
                          </div>
                        ))}
                      </div>
                    </details>
                  )}
                </div>
              ))}
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
