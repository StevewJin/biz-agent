import { useCallback, useEffect, useState } from 'react';
import { Cloud, Play, Plus, RefreshCw, Trash2 } from 'lucide-react';
import * as api from '../../services/api';
import type { ApiEndpointItem, ApiEndpointRequest } from '../../types/admin';
import { confirmDialog, toast } from '../../components/common/feedback';
import { useCapabilities } from '../../hooks/useCapabilities';
import {
  Alert, Badge, Button, Card, EmptyState, Field, IconButton, Input, Modal, PageHeader, Select, Textarea,
} from '../../components/admin/ui';

const EMPTY_FORM = {
  apiCode: '',
  apiName: '',
  baseUrl: '',
  path: '',
  httpMethod: 'GET',
  authType: 'none',
  authConfig: '',
  defaultHeaders: '',
  paramSchema: '',
  responseSchema: '',
  requestExample: '',
  timeoutMs: 30000,
  visibility: 'personal',
  description: '',
  enabled: true,
};

export default function ApiEndpointManagementPage() {
  const { canManage } = useCapabilities();
  const [items, setItems] = useState<ApiEndpointItem[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [testTarget, setTestTarget] = useState<ApiEndpointItem | null>(null);
  const [testParams, setTestParams] = useState('{}');
  const [testResult, setTestResult] = useState<string | null>(null);
  const [testing, setTesting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.getInterfaces();
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

  const openEdit = (item: ApiEndpointItem) => {
    setEditingId(item.id);
    setForm({
      apiCode: item.apiCode,
      apiName: item.apiName,
      baseUrl: item.baseUrl,
      path: item.path || '',
      httpMethod: item.httpMethod,
      authType: item.authType,
      authConfig: '',
      defaultHeaders: item.defaultHeaders ? JSON.stringify(item.defaultHeaders, null, 2) : '',
      paramSchema: item.paramSchema || '',
      responseSchema: item.responseSchema || '',
      requestExample: item.requestExample || '',
      timeoutMs: item.timeoutMs,
      visibility: item.visibility,
      description: item.description || '',
      enabled: item.enabled,
    });
    setShowForm(true);
  };

  const buildRequest = (): ApiEndpointRequest => {
    const request: ApiEndpointRequest = {
      apiCode: form.apiCode,
      apiName: form.apiName,
      baseUrl: form.baseUrl,
      path: form.path,
      httpMethod: form.httpMethod,
      authType: form.authType,
      paramSchema: form.paramSchema,
      responseSchema: form.responseSchema,
      requestExample: form.requestExample,
      timeoutMs: form.timeoutMs,
      visibility: form.visibility,
      description: form.description,
      enabled: form.enabled,
    };
    if (form.authConfig.trim()) {
      try {
        request.authConfig = JSON.parse(form.authConfig);
      } catch {
        throw new Error('鉴权配置不是合法 JSON');
      }
    }
    if (form.defaultHeaders.trim()) {
      try {
        request.defaultHeaders = JSON.parse(form.defaultHeaders);
      } catch {
        throw new Error('默认请求头不是合法 JSON');
      }
    }
    return request;
  };

  const save = async () => {
    try {
      const request = buildRequest();
      if (editingId) await api.updateInterface(editingId, request);
      else await api.createInterface(request);
      setShowForm(false);
      setError(null);
      toast.success('接口已保存');
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const openTest = (item: ApiEndpointItem) => {
    setTestTarget(item);
    setTestParams('{}');
    setTestResult(null);
  };

  const runTest = async () => {
    if (!testTarget) return;
    setTesting(true);
    try {
      const params = testParams.trim() ? JSON.parse(testParams) : {};
      const res = await api.testInterface(testTarget.id, params);
      setTestResult(res.data.result);
    } catch (e) {
      setTestResult(`调用失败：${(e as Error).message}`);
    } finally {
      setTesting(false);
    }
  };

  const remove = async (item: ApiEndpointItem) => {
    const ok = await confirmDialog({
      title: '删除接口',
      description: `确认删除「${item.apiName}」（${item.apiCode}）？引用该接口的技能包将无法调用。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteInterface(item.id);
      toast.success('接口已删除');
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const filtered = items.filter((item) => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return true;
    return item.apiName.toLowerCase().includes(keyword)
      || item.apiCode.toLowerCase().includes(keyword)
      || (item.baseUrl || '').toLowerCase().includes(keyword);
  });

  return (
    <div>
      <PageHeader
        title="业务接口"
        description="登记业务系统 HTTP 接口，扩展包只能调用这里登记的地址（域名白名单），鉴权由平台注入。"
        actions={
          <>
            <Button onClick={load}><RefreshCw size={14} /> 刷新</Button>
            <Button variant="primary" onClick={openCreate}><Plus size={15} /> 新建接口</Button>
          </>
        }
      />

      {error && <Alert onClose={() => setError(null)}>{error}</Alert>}

      {loading && items.length === 0 ? (
        <div className="py-16 text-center text-sm text-gray-400">加载中...</div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<Cloud size={18} />}
          title="还没有接口"
          description="登记业务系统接口后，技能包即可在提示词中引用接口能力"
          action={<Button variant="primary" onClick={openCreate}><Plus size={15} /> 新建接口</Button>}
        />
      ) : (
        <Card flush>
          <div className="flex items-center justify-between gap-3 border-b border-gray-100 px-5 py-3">
            <div className="text-sm font-semibold text-gray-900">
              接口列表 <span className="ml-1 font-normal text-gray-400">共 {items.length} 个</span>
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
                  <Cloud size={16} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium text-gray-900">{item.apiName}</span>
                    <code className="text-[11px] text-gray-400">{item.apiCode}</code>
                    <Badge tone="blue">{item.httpMethod}</Badge>
                    <Badge>鉴权 {item.authType}</Badge>
                    {!item.enabled && <Badge>已禁用</Badge>}
                  </div>
                  <div className="mt-1 truncate font-mono text-[11px] text-gray-400">{item.baseUrl}{item.path}</div>
                  <div className="mt-1 text-[11px] text-gray-400">超时 {item.timeoutMs}ms</div>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <IconButton aria-label="试调" onClick={() => openTest(item)}><Play size={15} /></IconButton>
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
              <div className="px-5 py-10 text-center text-sm text-gray-400">没有匹配的接口</div>
            )}
          </div>
        </Card>
      )}

      {showForm && (
        <Modal
          title={editingId ? '编辑接口' : '新建接口'}
          description="路径支持 {param} 占位符；鉴权配置加密保存"
          onClose={() => setShowForm(false)}
          footer={
            <>
              <Button onClick={() => setShowForm(false)}>取消</Button>
              <Button variant="primary" onClick={save}>保存</Button>
            </>
          }
        >
          <div className="grid grid-cols-2 gap-4">
            <Field label="接口编码" required>
              <Input value={form.apiCode} onChange={(e) => setForm({ ...form, apiCode: e.target.value })} placeholder="crm_order" />
            </Field>
            <Field label="接口名称" required>
              <Input value={form.apiName} onChange={(e) => setForm({ ...form, apiName: e.target.value })} />
            </Field>
            <Field label="服务地址" required className="col-span-2" hint="作为域名白名单使用，必须以 http:// 或 https:// 开头">
              <Input value={form.baseUrl} onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} placeholder="https://crm.example.com" />
            </Field>
            <Field label="路径">
              <Input value={form.path} onChange={(e) => setForm({ ...form, path: e.target.value })} placeholder="/api/orders/{orderId}" />
            </Field>
            <Field label="方法">
              <Select value={form.httpMethod} onChange={(e) => setForm({ ...form, httpMethod: e.target.value })}>
                {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((m) => <option key={m} value={m}>{m}</option>)}
              </Select>
            </Field>
            <Field label="鉴权类型">
              <Select value={form.authType} onChange={(e) => setForm({ ...form, authType: e.target.value })}>
                <option value="none">无需鉴权</option>
                <option value="bearer">Bearer Token</option>
                <option value="basic">Basic Auth</option>
                <option value="api_key">API Key</option>
              </Select>
            </Field>
            <Field label="超时（毫秒）">
              <Input type="number" value={form.timeoutMs} onChange={(e) => setForm({ ...form, timeoutMs: Number(e.target.value) })} />
            </Field>
            <Field label="鉴权配置 JSON" className="col-span-2" hint={editingId ? '留空表示不修改' : '如 {"token":"xxx"} / {"username":"u","password":"p"} / {"header":"X-API-Key","key":"xxx"}'}>
              <Textarea rows={2} className="font-mono text-xs" value={form.authConfig} onChange={(e) => setForm({ ...form, authConfig: e.target.value })} />
            </Field>
            <Field label="默认请求头 JSON" className="col-span-2">
              <Textarea rows={2} className="font-mono text-xs" value={form.defaultHeaders} onChange={(e) => setForm({ ...form, defaultHeaders: e.target.value })} placeholder='{"Content-Type":"application/json"}' />
            </Field>
            <Field label="参数说明" className="col-span-2" hint="JSON Schema 或文字说明，会注入技能提示词">
              <Textarea rows={3} className="font-mono text-xs" value={form.paramSchema} onChange={(e) => setForm({ ...form, paramSchema: e.target.value })} placeholder='{"orderId":"订单号(必填)"}' />
            </Field>
            <Field label="请求示例" className="col-span-2">
              <Textarea rows={2} className="font-mono text-xs" value={form.requestExample} onChange={(e) => setForm({ ...form, requestExample: e.target.value })} />
            </Field>
            <Field label="可见范围">
              <Select value={form.visibility} onChange={(e) => setForm({ ...form, visibility: e.target.value })}>
                <option value="personal">仅本人</option>
                <option value="team">团队</option>
                <option value="public">全部登录用户</option>
              </Select>
            </Field>
            <Field label="描述" className="col-span-2">
              <Textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </Field>
          </div>
        </Modal>
      )}

      {testTarget && (
        <Modal
          title={`试调 · ${testTarget.apiName}`}
          description={`${testTarget.httpMethod} ${testTarget.baseUrl}${testTarget.path || ''}`}
          onClose={() => setTestTarget(null)}
          footer={
            <>
              <Button onClick={() => setTestTarget(null)}>关闭</Button>
              <Button variant="primary" onClick={runTest} disabled={testing}>
                {testing ? '调用中…' : '发送请求'}
              </Button>
            </>
          }
        >
          <Field label="请求参数（JSON）" hint="路径参数、查询参数或请求体字段">
            <Textarea
              rows={4}
              className="font-mono text-xs"
              value={testParams}
              onChange={(e) => setTestParams(e.target.value)}
            />
          </Field>
          {testResult && (
            <div className="mt-4">
              <div className="mb-1.5 text-xs font-medium text-gray-600">响应</div>
              <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-gray-50 p-3 text-xs leading-relaxed text-gray-600">
                {testResult}
              </pre>
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}
