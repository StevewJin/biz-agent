import { useCallback, useEffect, useState } from 'react';
import { Database, Plug, Plus, RefreshCw, Table2, Trash2, X } from 'lucide-react';
import * as api from '../../services/api';
import type { DatasourceItem, DatasourceRequest } from '../../types/admin';
import { confirmDialog, toast } from '../../components/common/feedback';
import { useCapabilities } from '../../hooks/useCapabilities';
import {
  Alert, Badge, Button, Card, EmptyState, Field, IconButton, Input, Modal, PageHeader, Select, Textarea,
} from '../../components/admin/ui';

const EMPTY_FORM: DatasourceRequest = {
  dsCode: '',
  dsName: '',
  dbType: 'postgresql',
  jdbcUrl: '',
  driverClass: '',
  driverJarPath: '',
  username: '',
  password: '',
  schemaName: 'public',
  readOnly: true,
  queryMaxRows: 2000,
  queryTimeoutSec: 30,
  visibility: 'personal',
  description: '',
  enabled: true,
};

export default function DatasourceManagementPage() {
  const { canManage } = useCapabilities();
  const [items, setItems] = useState<DatasourceItem[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<DatasourceRequest>(EMPTY_FORM);
  const [tables, setTables] = useState<{ tableName: string; comment: string }[] | null>(null);
  const [tablesOwner, setTablesOwner] = useState<DatasourceItem | null>(null);
  const [columns, setColumns] = useState<{ column: string; type: string; comment: string }[]>([]);
  const [activeTable, setActiveTable] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.getDatasources();
      setItems(res.data || []);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const openCreate = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setShowForm(true);
  };

  const openEdit = (item: DatasourceItem) => {
    setEditingId(item.id);
    setForm({
      dsCode: item.dsCode,
      dsName: item.dsName,
      dbType: item.dbType,
      jdbcUrl: item.jdbcUrl,
      driverClass: item.driverClass || '',
      driverJarPath: item.driverJarPath || '',
      username: item.username || '',
      password: '',
      schemaName: item.schemaName || '',
      readOnly: item.readOnly,
      queryMaxRows: item.queryMaxRows,
      queryTimeoutSec: item.queryTimeoutSec,
      visibility: item.visibility,
      description: item.description || '',
      enabled: item.enabled,
    });
    setShowForm(true);
  };

  const save = async () => {
    try {
      if (editingId) await api.updateDatasource(editingId, form);
      else await api.createDatasource(form);
      setShowForm(false);
      setError(null);
      toast.success('数据源已保存');
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const testConnection = async (item: DatasourceItem) => {
    try {
      await api.testDatasource(item.id);
      setError(null);
      toast.success(`「${item.dsName}」连接成功`);
    } catch (e) {
      setError(`连接失败：${(e as Error).message}`);
    }
    await load();
  };

  const introspect = async (item: DatasourceItem) => {
    try {
      const res = await api.introspectDatasource(item.id);
      setError(null);
      toast.success(`已同步 ${res.data.tables} 张表`);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const remove = async (item: DatasourceItem) => {
    const ok = await confirmDialog({
      title: '删除数据源',
      description: `确认删除「${item.dsName}」（${item.dsCode}）？引用该数据源的技能包将无法查询。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteDatasource(item.id);
      toast.success('数据源已删除');
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const openTables = async (item: DatasourceItem) => {
    setTablesOwner(item);
    setActiveTable(null);
    setColumns([]);
    const res = await api.getDatasourceTables(item.id);
    setTables(res.data || []);
  };

  const openColumns = async (tableName: string) => {
    if (!tablesOwner) return;
    setActiveTable(tableName);
    const res = await api.getDatasourceColumns(tablesOwner.id, tableName);
    setColumns(res.data || []);
  };

  const filtered = items.filter((item) => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return true;
    return item.dsName.toLowerCase().includes(keyword)
      || item.dsCode.toLowerCase().includes(keyword)
      || (item.jdbcUrl || '').toLowerCase().includes(keyword);
  });

  return (
    <div>
      <PageHeader
        title="业务数据库"
        description="登记业务库连接，技能包与扩展包通过 ds_code 引用；所有查询强制只读并受行数、超时限制。"
        actions={
          <>
            <Button onClick={load}><RefreshCw size={14} /> 刷新</Button>
            <Button variant="primary" onClick={openCreate}><Plus size={15} /> 新建数据源</Button>
          </>
        }
      />

      {error && <Alert onClose={() => setError(null)}>{error}</Alert>}

      {loading && items.length === 0 ? (
        <div className="py-16 text-center text-sm text-gray-400">加载中...</div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<Database size={18} />}
          title="还没有数据源"
          description="登记业务系统数据库，智能体即可在授权范围内查询业务数据"
          action={<Button variant="primary" onClick={openCreate}><Plus size={15} /> 新建数据源</Button>}
        />
      ) : (
        <Card flush>
          <div className="flex items-center justify-between gap-3 border-b border-gray-100 px-5 py-3">
            <div className="text-sm font-semibold text-gray-900">
              数据源列表 <span className="ml-1 font-normal text-gray-400">共 {items.length} 个</span>
            </div>
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索名称 / 编码 / 地址"
              className="h-8 w-56 text-[13px]"
            />
          </div>
          <div className="divide-y divide-gray-100">
            {filtered.map((item) => (
              <div key={item.id} className="flex items-start gap-3 px-5 py-4">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gray-50 text-gray-400">
                  <Database size={16} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium text-gray-900">{item.dsName}</span>
                    <code className="text-[11px] text-gray-400">{item.dsCode}</code>
                    <Badge>{item.dbType}</Badge>
                    {!item.enabled && <Badge>已禁用</Badge>}
                    {item.lastTestOk === true && <Badge tone="green">连接正常</Badge>}
                    {item.lastTestOk === false && <Badge tone="red">连接失败</Badge>}
                  </div>
                  <div className="mt-1 truncate font-mono text-[11px] text-gray-400">{item.jdbcUrl}</div>
                  <div className="mt-1 text-[11px] text-gray-400">
                    {item.schemaName ? `schema ${item.schemaName} · ` : ''}
                    {item.tableCount} 张表 · 最多 {item.queryMaxRows} 行 · {item.readOnly ? '只读' : '可写'}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <IconButton aria-label="测试连接" onClick={() => testConnection(item)}><Plug size={15} /></IconButton>
                  <IconButton aria-label="同步表结构" onClick={() => introspect(item)}><RefreshCw size={15} /></IconButton>
                  <IconButton aria-label="查看表结构" onClick={() => openTables(item)}><Table2 size={15} /></IconButton>
                  {canManage(item.ownerId, item.teamId) && (
                    <>
                      <Button size="sm" onClick={() => openEdit(item)}>编辑</Button>
                      <IconButton tone="danger" aria-label="删除" onClick={() => remove(item)}><Trash2 size={15} /></IconButton>
                    </>
                  )}
                </div>
              </div>
            ))}
            {filtered.length === 0 && (
              <div className="px-5 py-10 text-center text-sm text-gray-400">没有匹配的数据源</div>
            )}
          </div>
        </Card>
      )}

      {showForm && (
        <Modal
          title={editingId ? '编辑数据源' : '新建数据源'}
          description="密码会以 AES 加密保存，接口仅返回掩码"
          onClose={() => setShowForm(false)}
          footer={
            <>
              <Button onClick={() => setShowForm(false)}>取消</Button>
              <Button variant="primary" onClick={save}>保存</Button>
            </>
          }
        >
          <div className="grid grid-cols-2 gap-4">
            <Field label="数据源编码" required>
              <Input value={form.dsCode || ''} onChange={(e) => setForm({ ...form, dsCode: e.target.value })} placeholder="ds_sales" />
            </Field>
            <Field label="名称" required>
              <Input value={form.dsName || ''} onChange={(e) => setForm({ ...form, dsName: e.target.value })} placeholder="销售业务库" />
            </Field>
            <Field label="数据库类型">
              <Select value={form.dbType} onChange={(e) => setForm({ ...form, dbType: e.target.value })}>
                <option value="postgresql">PostgreSQL</option>
                <option value="panweidb">PanweiDB</option>
                <option value="mysql">MySQL</option>
                <option value="oracle">Oracle</option>
                <option value="sqlserver">SQL Server</option>
              </Select>
            </Field>
            <Field label="默认模式" hint="PostgreSQL 会用于设置 search_path">
              <Input value={form.schemaName || ''} onChange={(e) => setForm({ ...form, schemaName: e.target.value })} placeholder="public" />
            </Field>
            <Field label="JDBC 地址" required className="col-span-2">
              <Input value={form.jdbcUrl || ''} onChange={(e) => setForm({ ...form, jdbcUrl: e.target.value })} placeholder="jdbc:postgresql://host:5432/db" />
            </Field>
            <Field label="驱动类">
              <Input value={form.driverClass || ''} onChange={(e) => setForm({ ...form, driverClass: e.target.value })} placeholder="org.postgresql.Driver" />
            </Field>
            <Field label="驱动 jar 路径" hint="国产数据库驱动可放服务器路径">
              <Input value={form.driverJarPath || ''} onChange={(e) => setForm({ ...form, driverJarPath: e.target.value })} />
            </Field>
            <Field label="用户名">
              <Input value={form.username || ''} onChange={(e) => setForm({ ...form, username: e.target.value })} />
            </Field>
            <Field label="密码" hint={editingId ? '留空则不修改' : undefined}>
              <Input type="password" value={form.password || ''} onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </Field>
            <Field label="最大返回行数">
              <Input type="number" value={form.queryMaxRows || 2000} onChange={(e) => setForm({ ...form, queryMaxRows: Number(e.target.value) })} />
            </Field>
            <Field label="查询超时（秒）">
              <Input type="number" value={form.queryTimeoutSec || 30} onChange={(e) => setForm({ ...form, queryTimeoutSec: Number(e.target.value) })} />
            </Field>
            <Field label="可见范围">
              <Select value={form.visibility} onChange={(e) => setForm({ ...form, visibility: e.target.value })}>
                <option value="personal">仅本人</option>
                <option value="team">团队</option>
                <option value="public">全部登录用户</option>
              </Select>
            </Field>
            <label className="mt-6 inline-flex items-center gap-2 text-xs text-gray-600">
              <input type="checkbox" checked={form.readOnly !== false} onChange={(e) => setForm({ ...form, readOnly: e.target.checked })} />
              只读访问
            </label>
            <Field label="描述" className="col-span-2">
              <Textarea rows={2} value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </Field>
          </div>
        </Modal>
      )}

      {tablesOwner && (
        <Modal
          title={`表结构 · ${tablesOwner.dsName}`}
          description={tablesOwner.jdbcUrl}
          onClose={() => setTablesOwner(null)}
          width="max-w-4xl"
        >
          <div className="flex h-[60vh] overflow-hidden rounded-lg border border-gray-200">
            <div className="w-1/2 overflow-y-auto border-r border-gray-100">
              {(tables || []).map((t) => (
                <button
                  key={t.tableName}
                  onClick={() => openColumns(t.tableName)}
                  className={`block w-full border-b border-gray-50 px-4 py-2.5 text-left text-xs transition-colors cursor-pointer ${
                    activeTable === t.tableName ? 'bg-indigo-50/70 text-indigo-700' : 'text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  <div className="font-medium">{t.tableName}</div>
                  {t.comment && <div className="mt-0.5 text-gray-400">{t.comment}</div>}
                </button>
              ))}
              {tables && tables.length === 0 && (
                <div className="px-4 py-8 text-center text-xs text-gray-400">尚未同步表结构，请先点击「同步表结构」</div>
              )}
            </div>
            <div className="w-1/2 overflow-y-auto p-4">
              {activeTable ? (
                <>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-xs font-medium text-gray-700">{activeTable}</span>
                    <IconButton aria-label="清空" onClick={() => { setActiveTable(null); setColumns([]); }}><X size={14} /></IconButton>
                  </div>
                  {columns.map((c) => (
                    <div key={c.column} className="flex gap-2 border-b border-gray-50 py-1.5 text-[11px]">
                      <span className="w-36 shrink-0 font-medium text-gray-700">{c.column}</span>
                      <span className="w-24 shrink-0 text-gray-400">{c.type}</span>
                      <span className="truncate text-gray-400">{c.comment}</span>
                    </div>
                  ))}
                </>
              ) : (
                <div className="flex h-full items-center justify-center text-xs text-gray-400">选择左侧表查看字段</div>
              )}
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
