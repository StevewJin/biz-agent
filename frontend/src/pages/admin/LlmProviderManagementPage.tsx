import { useEffect, useState } from 'react';
import { useAdminStore } from '../../stores/adminStore';
import { Cpu, Plus, Star, Trash2 } from 'lucide-react';
import * as api from '../../services/api';
import { confirmDialog, toast } from '../../components/common/feedback';
import {
  Alert, Badge, Button, Card, EmptyState, Field, IconButton, Input, Modal, PageHeader, Select,
} from '../../components/admin/ui';

export default function LlmProviderManagementPage() {
  const { providers, loadProviders, createProvider, deleteProvider, error } = useAdminStore();
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: '', providerType: 'deepseek', apiKey: '', baseUrl: '', models: '' });

  useEffect(() => { loadProviders(); }, [loadProviders]);

  const handleCreate = async () => {
    if (!form.name.trim()) return;
    await createProvider({ ...form, models: form.models.split(',').map((s) => s.trim()).filter(Boolean) });
    toast.success('供应商已添加');
    setShowCreate(false);
    setForm({ name: '', providerType: 'deepseek', apiKey: '', baseUrl: '', models: '' });
  };

  const handleSetDefault = async (id: number) => {
    await api.setDefaultProvider(id);
    toast.success('已切换默认供应商');
    loadProviders();
  };

  const handleDelete = async (id: number, name: string) => {
    const ok = await confirmDialog({
      title: '删除供应商',
      description: `确认删除「${name}」？使用该供应商的智能体将回退到平台默认。`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    await deleteProvider(id);
    toast.success('供应商已删除');
  };

  return (
    <div>
      <PageHeader
        title="LLM 供应商"
        description="平台优先使用这里配置的供应商；未配置时回退到 application.yml 中的默认 DeepSeek 配置。"
        actions={<Button variant="primary" onClick={() => setShowCreate(true)}><Plus size={15} /> 添加供应商</Button>}
      />

      {error && <Alert>{error}</Alert>}

      <Card flush title="供应商列表" description={`共 ${providers.length} 个`}>
        {providers.length === 0 ? (
          <EmptyState
            icon={<Cpu size={18} />}
            title="尚未配置供应商"
            description="当前使用 application.yml 中的 DeepSeek 默认配置"
            action={<Button variant="primary" onClick={() => setShowCreate(true)}><Plus size={15} /> 添加供应商</Button>}
          />
        ) : (
          <div className="divide-y divide-gray-100">
            {providers.map((p) => (
              <div key={p.id} className="flex items-center gap-3 px-5 py-4">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gray-50 text-gray-400">
                  <Cpu size={16} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-900">{p.name}</span>
                    <Badge>{p.providerType}</Badge>
                    {p.isDefault && <Badge tone="amber"><Star size={10} /> 默认</Badge>}
                  </div>
                  <div className="mt-0.5 truncate text-xs text-gray-400">
                    {p.baseUrl || '默认地址'} · {p.models?.length ? p.models.join(', ') : '未配置模型'}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  {!p.isDefault && (
                    <Button size="sm" onClick={() => handleSetDefault(p.id)}>设为默认</Button>
                  )}
                  <IconButton tone="danger" aria-label="删除供应商" onClick={() => handleDelete(p.id, p.name)}>
                    <Trash2 size={14} />
                  </IconButton>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      {showCreate && (
        <Modal
          title="添加供应商"
          description="API Key 会以 AES 加密存储，接口不返回明文"
          onClose={() => setShowCreate(false)}
          footer={
            <>
              <Button onClick={() => setShowCreate(false)}>取消</Button>
              <Button variant="primary" onClick={handleCreate}>保存</Button>
            </>
          }
        >
          <div className="grid grid-cols-2 gap-4">
            <Field label="名称" required>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="如：DeepSeek 官方" />
            </Field>
            <Field label="类型">
              <Select value={form.providerType} onChange={(e) => setForm({ ...form, providerType: e.target.value })}>
                <option value="deepseek">DeepSeek</option>
                <option value="openai">OpenAI</option>
                <option value="claude">Claude</option>
                <option value="ollama">Ollama</option>
              </Select>
            </Field>
            <Field label="API Key" required className="col-span-2">
              <Input type="password" value={form.apiKey} onChange={(e) => setForm({ ...form, apiKey: e.target.value })} placeholder="sk-..." />
            </Field>
            <Field label="Base URL" className="col-span-2" hint="OpenAI 兼容网关填写完整前缀，如 https://api.deepseek.com">
              <Input value={form.baseUrl} onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} placeholder="https://api.deepseek.com" />
            </Field>
            <Field label="模型列表" className="col-span-2" hint="多个模型用英文逗号分隔，第一个为默认模型">
              <Input value={form.models} onChange={(e) => setForm({ ...form, models: e.target.value })} placeholder="deepseek-flash, deepseek-v4-pro" />
            </Field>
          </div>
        </Modal>
      )}
    </div>
  );
}
