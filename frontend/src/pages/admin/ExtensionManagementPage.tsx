import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, Plus, Power, PowerOff, RefreshCw, Trash2 } from 'lucide-react';
import * as api from '../../services/api';
import { resetExtensionLoader } from '../../extensions/loader';
import { confirmDialog, toast } from '../../components/common/feedback';
import type { ExtensionItem } from '../../types/admin';
import { useCapabilities } from '../../hooks/useCapabilities';
import {
  Alert, Badge, Button, Card, EmptyState, IconButton, Input, PageHeader,
} from '../../components/admin/ui';

/** 上架状态徽标 */
function publishBadge(status: string) {
  if (status === 'pending') return <Badge tone="amber">待审核</Badge>;
  if (status === 'approved') return <Badge tone="green">已上架</Badge>;
  if (status === 'rejected') return <Badge tone="red">已驳回</Badge>;
  return <Badge tone="gray">未上架</Badge>;
}

export default function ExtensionManagementPage() {
  const { canManage } = useCapabilities();
  const [items, setItems] = useState<ExtensionItem[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.getExtensions();
      setItems(res.data || []);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleImport = async (file: File) => {
    setUploading(true);
    try {
      const res = await api.importExtension(file);
      setError(null);
      toast.success(`扩展包 ${res.data.extCode} v${res.data.version} 导入成功`);
      resetExtensionLoader();
      await load();
    } catch (e) {
      setError(`导入失败：${(e as Error).message}`);
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const toggle = async (item: ExtensionItem) => {
    try {
      if (item.status === 'enabled') await api.disableExtension(item.extCode);
      else await api.enableExtension(item.extCode);
      resetExtensionLoader();
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const reload = async (item: ExtensionItem) => {
    try {
      await api.reloadExtension(item.extCode);
      setError(null);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const uninstall = async (item: ExtensionItem) => {
    const ok = await confirmDialog({
      title: '卸载扩展包',
      description: `确认卸载「${item.extName}」？引用其工具的技能包会失效。`,
      confirmText: '卸载',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.uninstallExtension(item.extCode);
      toast.success('扩展包已卸载');
      resetExtensionLoader();
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const handlePublish = async (item: ExtensionItem) => {
    const ok = await confirmDialog({
      title: '申请上架',
      description: `将「${item.extName}」提交平台审核，通过后所有用户可见。`,
      confirmText: '提交申请',
    });
    if (!ok) return;
    try {
      await api.publishExtension(item.extCode);
      toast.success('已提交上架申请');
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const handleWithdraw = async (item: ExtensionItem) => {
    const ok = await confirmDialog({
      title: item.publishStatus === 'pending' ? '撤回申请' : '下架',
      description: `确认将「${item.extName}」退出公开可见？`,
      confirmText: '确认',
      danger: item.publishStatus === 'approved',
    });
    if (!ok) return;
    try {
      await api.withdrawExtension(item.extCode);
      toast.success('已撤出上架');
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const filtered = items.filter((item) => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return true;
    return item.extName.toLowerCase().includes(keyword)
      || item.extCode.toLowerCase().includes(keyword)
      || (item.description || '').toLowerCase().includes(keyword);
  });

  return (
    <div>
      <PageHeader
        title="扩展包"
        description="工具与前端渲染能力全部由扩展包提供。声明式扩展包人人可导入；含 jar 代码的仅管理员。可申请上架，审核通过后全平台可用。"
        actions={
          <>
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索扩展包"
              className="h-9 w-44 text-[13px]"
            />
            <Button onClick={load}><RefreshCw size={14} /> 刷新</Button>
            <Button variant="primary" onClick={() => fileRef.current?.click()} disabled={uploading}>
              {uploading ? <RefreshCw size={15} className="animate-spin" /> : <Plus size={15} />} 导入扩展包
            </Button>
            <input
              ref={fileRef}
              type="file"
              accept=".zip,.bpk,.jar"
              className="hidden"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) handleImport(f); }}
            />
          </>
        }
      />

      {error && <Alert onClose={() => setError(null)}>{error}</Alert>}

      {loading && items.length === 0 ? (
        <div className="py-16 text-center text-sm text-gray-400">加载中...</div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<Package size={18} />}
          title="还没有扩展包"
          description="导入 .bpk / zip 扩展包以启用数据库查询、接口调用、图表渲染等能力"
          action={<Button variant="primary" onClick={() => fileRef.current?.click()}><Plus size={15} /> 导入扩展包</Button>}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          {filtered.map((item) => (
            <Card key={item.id} className="transition-shadow hover:shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gray-50 text-gray-400">
                  <Package size={16} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link to={`/admin/extensions/${item.extCode}`} className="text-sm font-semibold text-gray-900 hover:text-indigo-600">
                      {item.extName}
                    </Link>
                    <code className="text-[11px] text-gray-400">{item.extCode} v{item.version}</code>
                    <Badge tone={item.status === 'enabled' ? 'green' : item.status === 'error' ? 'red' : 'gray'}>
                      {item.status}
                    </Badge>
                    {item.systemPack && <Badge tone="amber">内置</Badge>}
                    {item.packType !== 'declarative' && <Badge tone="indigo">{item.packType}</Badge>}
                    {publishBadge(item.publishStatus)}
                    {item.visibility === 'team' && <Badge tone="blue">团队</Badge>}
                  </div>
                  {item.description && <p className="mt-1 text-xs leading-relaxed text-gray-500">{item.description}</p>}
                  <div className="mt-1.5 text-[11px] text-gray-400">
                    {item.tools.length} 个工具 · {item.renderers.length} 个渲染器
                    {item.vendor ? ` · ${item.vendor}` : ''}
                    {item.loaded ? ' · 已加载' : ''}
                  </div>
                  {item.errorMsg && <div className="mt-1 text-[11px] text-red-600">加载错误：{item.errorMsg}</div>}
                  {item.publishStatus === 'rejected' && item.rejectReason && (
                    <div className="mt-1 text-[11px] text-amber-600">审核未通过：{item.rejectReason}</div>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  {canManage(item.ownerId, item.teamId) && (
                  <IconButton aria-label={item.status === 'enabled' ? '禁用' : '启用'} onClick={() => toggle(item)}>
                    {item.status === 'enabled' ? <PowerOff size={15} /> : <Power size={15} />}
                  </IconButton>
                  )}
                  {canManage(item.ownerId, item.teamId) && (
                  <IconButton aria-label="重载" onClick={() => reload(item)}><RefreshCw size={15} /></IconButton>
                  )}
                  {!item.systemPack && canManage(item.ownerId, item.teamId) && (
                    <IconButton tone="danger" aria-label="卸载" onClick={() => uninstall(item)}><Trash2 size={15} /></IconButton>
                  )}
                  {!item.systemPack && canManage(item.ownerId, item.teamId)
                    && item.publishStatus === 'draft' && (
                    <Button size="sm" onClick={() => handlePublish(item)}>申请上架</Button>
                  )}
                  {!item.systemPack && canManage(item.ownerId, item.teamId)
                    && (item.publishStatus === 'pending' || item.publishStatus === 'approved'
                        || item.publishStatus === 'rejected') && (
                    <Button size="sm" variant="ghost" onClick={() => handleWithdraw(item)}>
                      {item.publishStatus === 'pending' ? '撤回申请' : '下架'}
                    </Button>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Card className="mt-5" title="扩展包结构" description="打包为 zip 后可直接在页面导入">
        <pre className="overflow-x-auto rounded-lg bg-gray-50 p-4 text-[11px] leading-relaxed text-gray-500">
{`chart-pro/
├── manifest.json        # 必需：工具与渲染器声明
├── lib/plugin.jar       # 可选：代码类工具（仅管理员可导入）
├── ui/entry.js          # 可选：前端渲染器（IIFE，共享宿主 React/Recharts）
└── resources/           # 可选：SQL 模板等`}
        </pre>
      </Card>
    </div>
  );
}
