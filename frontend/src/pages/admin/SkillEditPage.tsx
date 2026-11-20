import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Eye, Plus, Upload } from 'lucide-react';
import * as api from '../../services/api';
import { useSkillStore } from '../../stores/skillStore';
import type { Skill } from '../../types';
import type { DatasourceItem, ApiEndpointItem, ExtensionItem } from '../../types/admin';
import {
  Alert, Badge, Button, Card, Field, Input, PageHeader, Select, Textarea,
} from '../../components/admin/ui';

interface ToolOption {
  toolKey: string;
  displayName: string;
  toolKind: string;
  riskLevel: string;
}

export default function SkillEditPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { loadSkills } = useSkillStore();
  const isCreate = !id;

  const [form, setForm] = useState({
    name: '',
    displayName: '',
    description: '',
    category: 'general',
    icon: 'zap',
    iconColor: '',
    keywords: '',
    triggerMode: 'auto',
    schemaMode: 'inline',
    skillVersion: '1.0.0',
    promptMd: '',
  });
  const [resources, setResources] = useState<{ datasources: string[]; interfaces: string[]; extensions: string[]; tools: string[] }>({
    datasources: [], interfaces: [], extensions: [], tools: [],
  });
  const [datasources, setDatasources] = useState<DatasourceItem[]>([]);
  const [interfaces, setInterfaces] = useState<ApiEndpointItem[]>([]);
  const [extensions, setExtensions] = useState<ExtensionItem[]>([]);
  const [tools, setTools] = useState<ToolOption[]>([]);
  const [preview, setPreview] = useState<{ prompt: string; toolKeys: string[]; warnings: string[] } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadOptions = useCallback(async () => {
    const [ds, api2, ext, tool] = await Promise.all([
      api.getDatasources().catch(() => ({ data: [] as DatasourceItem[] })),
      api.getInterfaces().catch(() => ({ data: [] as ApiEndpointItem[] })),
      api.getExtensions().catch(() => ({ data: [] as ExtensionItem[] })),
      api.getExtensionTools().catch(() => ({ data: [] as ToolOption[] })),
    ]);
    setDatasources(ds.data || []);
    setInterfaces(api2.data || []);
    setExtensions((ext.data || []).filter((e) => e.status === 'enabled'));
    setTools(tool.data || []);
  }, []);

  useEffect(() => { loadOptions(); }, [loadOptions]);

  useEffect(() => {
    if (isCreate) return;
    api.getSkill(Number(id)).then((r) => {
      const skill: Skill = r.data;
      setForm({
        name: skill.name,
        displayName: skill.displayName,
        description: skill.description || '',
        category: skill.category || 'general',
        icon: skill.icon || 'zap',
        iconColor: skill.iconColor || '',
        keywords: skill.keywords || '',
        triggerMode: skill.triggerMode || 'auto',
        schemaMode: skill.schemaMode || 'inline',
        skillVersion: skill.skillVersion || '1.0.0',
        promptMd: skill.promptMd || '',
      });
      setResources({
        datasources: skill.resources?.datasources || [],
        interfaces: skill.resources?.interfaces || [],
        extensions: skill.resources?.extensions || [],
        tools: skill.resources?.tools || [],
      });
      api.previewSkill(Number(id)).then((p) => setPreview(p.data)).catch(() => undefined);
    });
  }, [id, isCreate]);

  const set = (k: string, v: string) => setForm({ ...form, [k]: v });
  const toolKeySet = useMemo(() => new Set(resources.tools), [resources.tools]);

  /** 提示词中引用的占位符，用于即时校验 */
  const issues = useMemo(() => {
    const pattern = /\{\{\s*(datasource|db|api|interface|extension|ext|tool)\s*:\s*([A-Za-z0-9_.\-]+)\s*}}/g;
    const problems: string[] = [];
    let matched: RegExpExecArray | null;
    let count = 0;
    while ((matched = pattern.exec(form.promptMd)) !== null) {
      count++;
      const kind = matched[1];
      const code = matched[2];
      if ((kind === 'datasource' || kind === 'db') && !datasources.some((d) => d.dsCode === code)) {
        problems.push(`数据源 ${code} 不存在或无权访问`);
      }
      if ((kind === 'api' || kind === 'interface') && !interfaces.some((i) => i.apiCode === code)) {
        problems.push(`接口 ${code} 不存在或无权访问`);
      }
      if ((kind === 'extension' || kind === 'ext') && !extensions.some((e) => e.extCode === code)) {
        problems.push(`扩展包 ${code} 未安装或未启用`);
      }
      if (kind === 'tool' && !tools.some((t) => t.toolKey === code)) {
        problems.push(`工具 ${code} 不存在或未启用`);
      }
    }
    if (count === 0 && resources.tools.length === 0) {
      problems.push('提示词未引用任何资源且未绑定工具，运行时只会注入低风险工具');
    }
    return Array.from(new Set(problems));
  }, [form.promptMd, datasources, interfaces, extensions, tools, resources.tools]);

  const toggleTool = (toolKey: string) => {
    setResources((prev) => ({
      ...prev,
      tools: prev.tools.includes(toolKey)
        ? prev.tools.filter((t) => t !== toolKey)
        : [...prev.tools, toolKey],
    }));
  };

  const insertPlaceholder = (text: string) => {
    setForm((prev) => ({
      ...prev,
      promptMd: `${prev.promptMd}${prev.promptMd.endsWith('\n') || !prev.promptMd ? '' : '\n'}${text}\n`,
    }));
  };

  const buildPayload = () => ({
    ...form,
    resources,
    requiredTools: resources.tools,
  });

  const handleSave = async () => {
    setLoading(true);
    setError(null);
    try {
      if (isCreate) await api.createSkill(buildPayload());
      else await api.updateSkill(Number(id), buildPayload());
      loadSkills();
      navigate('/admin/skills');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const handlePreview = async () => {
    try {
      const res = await api.previewSkillDraft(buildPayload());
      setPreview(res.data);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const handleImport = async (file: File) => {
    try {
      await api.importSkill(file);
      loadSkills();
      navigate('/admin/skills');
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div>
      <button
        onClick={() => navigate('/admin/skills')}
        className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-gray-500 transition-colors hover:text-gray-800 cursor-pointer"
      >
        <ArrowLeft size={15} /> 返回技能包列表
      </button>

      <PageHeader
        title={isCreate ? '创建技能包' : '编辑技能包'}
        description="提示词中插入资源占位符后，运行时自动展开为表结构、接口参数与扩展工具，并据此授权工具。"
        actions={
          <>
            {isCreate && (
              <label className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50">
                <Upload size={15} /> 导入
                <input type="file" accept=".zip" className="hidden"
                  onChange={(e) => { const f = e.target.files?.[0]; if (f) handleImport(f); }} />
              </label>
            )}
            <Button onClick={() => navigate('/admin/skills')}>取消</Button>
            <Button variant="primary" onClick={handleSave} disabled={loading}>
              {loading ? '保存中…' : '保存'}
            </Button>
          </>
        }
      />

      {error && <Alert onClose={() => setError(null)}>{error}</Alert>}

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1.55fr)_minmax(0,1fr)]">
        <div className="space-y-5">
          <Card title="基本信息">
            <div className="grid grid-cols-2 gap-4">
              <Field label="技能编码" required hint={isCreate ? '唯一标识，创建后不可修改' : '不可修改'}>
                <Input value={form.name} onChange={(e) => set('name', e.target.value)}
                  placeholder="如 sales_analysis" disabled={!isCreate} />
              </Field>
              <Field label="显示名称" required>
                <Input value={form.displayName} onChange={(e) => set('displayName', e.target.value)} placeholder="如 销售分析" />
              </Field>
              <Field label="描述" className="col-span-2">
                <Input value={form.description} onChange={(e) => set('description', e.target.value)} placeholder="一句话说明该技能包的能力" />
              </Field>
              <Field label="分类">
                <Select value={form.category} onChange={(e) => set('category', e.target.value)}>
                  <option value="general">通用</option>
                  <option value="data">数据分析</option>
                  <option value="report">报告生成</option>
                  <option value="automation">自动化</option>
                </Select>
              </Field>
              <Field label="触发方式">
                <Select value={form.triggerMode} onChange={(e) => set('triggerMode', e.target.value)}>
                  <option value="auto">自动（始终生效）</option>
                  <option value="keyword">关键词触发</option>
                  <option value="manual">手动激活</option>
                </Select>
              </Field>
              <Field label="触发关键词" hint="多个关键词用逗号分隔">
                <Input value={form.keywords} onChange={(e) => set('keywords', e.target.value)} placeholder="销售,业绩,趋势" />
              </Field>
              <Field label="表结构注入" hint="lazy 只注入表名，由模型按需查询字段，可显著降低 token 消耗">
                <Select value={form.schemaMode} onChange={(e) => set('schemaMode', e.target.value)}>
                  <option value="inline">inline（内联表字段）</option>
                  <option value="lazy">lazy（仅表名，按需查询）</option>
                </Select>
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field label="图标">
                  <Input value={form.icon} onChange={(e) => set('icon', e.target.value)} placeholder="bar-chart-3" />
                </Field>
                <Field label="版本">
                  <Input value={form.skillVersion} onChange={(e) => set('skillVersion', e.target.value)} />
                </Field>
              </div>
            </div>
          </Card>

          <Card
            title="提示词"
            description="支持 Markdown；使用占位符引用资源可自动注入表结构、接口参数与工具说明"
            actions={
              <Button size="sm" onClick={handlePreview}><Eye size={14} /> 解析预览</Button>
            }
          >
            {issues.length > 0 && (
              <div className="mb-3">
                <Alert tone="warning">
                  {issues.map((issue) => <div key={issue}>· {issue}</div>)}
                </Alert>
              </div>
            )}
            <Textarea
              value={form.promptMd}
              onChange={(e) => set('promptMd', e.target.value)}
              rows={16}
              placeholder={'你是业务分析专家…\n\n{{datasource:ds_sales}}\n{{api:crm_order}}'}
              className="font-mono text-[13px]"
              style={{ tabSize: 2 }}
            />

            <div className="mt-4">
              <div className="mb-2 text-xs font-medium text-gray-600">插入资源占位符</div>
              <div className="flex flex-wrap gap-1.5">
                {datasources.map((ds) => (
                  <button key={ds.dsCode} onClick={() => insertPlaceholder(`{{datasource:${ds.dsCode}}}`)}
                    className="inline-flex items-center gap-1 rounded-md border border-sky-100 bg-sky-50 px-2 py-1 text-[11px] text-sky-700 transition-colors hover:bg-sky-100 cursor-pointer">
                    <Plus size={10} /> {ds.dsName}
                  </button>
                ))}
                {interfaces.map((ep) => (
                  <button key={ep.apiCode} onClick={() => insertPlaceholder(`{{api:${ep.apiCode}}}`)}
                    className="inline-flex items-center gap-1 rounded-md border border-emerald-100 bg-emerald-50 px-2 py-1 text-[11px] text-emerald-700 transition-colors hover:bg-emerald-100 cursor-pointer">
                    <Plus size={10} /> {ep.apiName}
                  </button>
                ))}
                {extensions.map((ext) => (
                  <button key={ext.extCode} onClick={() => insertPlaceholder(`{{extension:${ext.extCode}}}`)}
                    className="inline-flex items-center gap-1 rounded-md border border-indigo-100 bg-indigo-50 px-2 py-1 text-[11px] text-indigo-700 transition-colors hover:bg-indigo-100 cursor-pointer">
                    <Plus size={10} /> {ext.extName}
                  </button>
                ))}
                {datasources.length + interfaces.length + extensions.length === 0 && (
                  <span className="text-xs text-gray-400">暂无可用资源，请先登记数据源 / 接口或安装扩展包</span>
                )}
              </div>
            </div>
          </Card>

          <Card title="工具白名单" description="不勾选时按提示词中的占位符自动推导">
            <div className="overflow-hidden rounded-lg border border-gray-200">
              {tools.map((tool) => (
                <label key={tool.toolKey} className="flex cursor-pointer items-center gap-3 border-b border-gray-50 px-3 py-2 text-xs text-gray-600 last:border-0 hover:bg-gray-50">
                  <input type="checkbox" checked={toolKeySet.has(tool.toolKey)} onChange={() => toggleTool(tool.toolKey)} />
                  <code className="font-mono text-gray-700">{tool.toolKey}</code>
                  <Badge>{tool.toolKind}</Badge>
                  <Badge tone={tool.riskLevel === 'high' ? 'red' : tool.riskLevel === 'medium' ? 'amber' : 'green'}>{tool.riskLevel}</Badge>
                </label>
              ))}
              {tools.length === 0 && <div className="px-3 py-4 text-xs text-gray-400">暂无可用工具，请先安装扩展包</div>}
            </div>
          </Card>
        </div>

        <div className="xl:sticky xl:top-6 xl:self-start">
          <Card title="解析预览" description="占位符展开后的系统提示词">
            {preview?.warnings && preview.warnings.length > 0 && (
              <Alert tone="warning">
                {preview.warnings.map((w) => <div key={w}>· {w}</div>)}
              </Alert>
            )}
            {preview ? (
              <>
                <div className="mb-3 flex flex-wrap gap-1">
                  {preview.toolKeys.length > 0
                    ? preview.toolKeys.map((k) => (
                        <code key={k} className="rounded bg-gray-50 px-1.5 py-0.5 text-[11px] text-gray-600">{k}</code>
                      ))
                    : <span className="text-xs text-gray-400">未推导出工具</span>}
                </div>
                <pre className="max-h-[62vh] overflow-y-auto whitespace-pre-wrap break-words rounded-lg bg-gray-50 p-3 text-[11px] leading-relaxed text-gray-600">
                  {preview.prompt}
                </pre>
              </>
            ) : (
              <div className="rounded-lg border border-dashed border-gray-200 px-4 py-10 text-center text-xs text-gray-400">
                点击「解析预览」查看展开结果
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
