import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Boxes, Shield, Wrench } from 'lucide-react';
import * as api from '../../services/api';
import type { ExtensionItem } from '../../types/admin';
import {
  Alert, Badge, Card, PageHeader, Stat,
} from '../../components/admin/ui';

export default function ExtensionDetailPage() {
  const { code } = useParams<{ code: string }>();
  const [item, setItem] = useState<ExtensionItem | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!code) return;
    try {
      const res = await api.getExtension(code);
      setItem(res.data);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [code]);

  useEffect(() => { load(); }, [load]);

  if (error) return <Alert>{error}</Alert>;
  if (!item) return <div className="py-16 text-center text-sm text-gray-400">加载中...</div>;

  return (
    <div>
      <Link
        to="/admin/extensions"
        className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-gray-500 transition-colors hover:text-gray-800"
      >
        <ArrowLeft size={15} /> 返回扩展包列表
      </Link>

      <PageHeader
        title={item.extName}
        description={item.description || undefined}
        actions={
          <>
            <Badge tone={item.status === 'enabled' ? 'green' : item.status === 'error' ? 'red' : 'gray'}>{item.status}</Badge>
            {item.systemPack && <Badge tone="amber">系统内置</Badge>}
            {item.loaded && <Badge tone="indigo">已加载</Badge>}
          </>
        }
      />

      <div className="mb-5 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="编码" value={<code className="text-sm">{item.extCode}</code>} hint={`v${item.version} · ${item.packType}`} />
        <Stat label="工具数量" value={item.tools.length} />
        <Stat label="前端渲染器" value={item.renderers.length} />
        <Stat label="供应商" value={item.vendor || '-'} hint={item.entryFile ? `入口 ${item.entryFile}` : undefined} />
      </div>

      <Card
        className="mb-5"
        title={<span className="inline-flex items-center gap-1.5"><Wrench size={14} className="text-gray-400" /> 工具清单</span>}
        description="工具会按技能包授权注入到智能体"
        flush
      >
        {item.tools.length === 0 ? (
          <div className="px-5 py-8 text-center text-sm text-gray-400">该扩展包未声明工具</div>
        ) : (
          <div className="divide-y divide-gray-100">
            {item.tools.map((tool) => (
              <div key={tool.toolKey} className="px-5 py-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium text-gray-900">{tool.displayName || tool.toolCode}</span>
                  <code className="font-mono text-[11px] text-gray-400">{tool.toolKey}</code>
                  <Badge>{tool.toolKind}</Badge>
                  <Badge tone={tool.riskLevel === 'high' ? 'red' : tool.riskLevel === 'medium' ? 'amber' : 'green'}>
                    风险 {tool.riskLevel}
                  </Badge>
                  <Badge tone={tool.anonymousAllowed ? 'blue' : 'gray'}>
                    {tool.anonymousAllowed ? '匿名可用' : '需登录'}
                  </Badge>
                  {tool.renderType && <Badge tone="indigo">渲染 {tool.renderType}</Badge>}
                </div>
                {tool.description && <p className="mt-1.5 text-xs leading-relaxed text-gray-500">{tool.description}</p>}
                {tool.parameterSchema && (
                  <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-gray-50 p-2.5 text-[11px] leading-relaxed text-gray-500">
                    {tool.parameterSchema}
                  </pre>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card
        title={<span className="inline-flex items-center gap-1.5"><Boxes size={14} className="text-gray-400" /> 前端渲染器</span>}
        description="前端按渲染类型按需加载扩展包脚本"
        flush
      >
        {item.renderers.length === 0 ? (
          <div className="px-5 py-8 text-center text-sm text-gray-400">该扩展包未提供前端渲染器</div>
        ) : (
          <div className="divide-y divide-gray-100">
            {item.renderers.map((renderer) => (
              <div key={renderer.renderType} className="flex items-center gap-3 px-5 py-3 text-xs">
                <code className="font-mono text-gray-700">{renderer.renderType}</code>
                <span className="truncate text-gray-400">{renderer.entry || item.entryFile || '-'}</span>
              </div>
            ))}
          </div>
        )}
      </Card>

      <div className="mt-5 flex items-start gap-2 rounded-lg bg-gray-50 px-4 py-3 text-[11px] leading-relaxed text-gray-500">
        <Shield size={14} className="mt-0.5 shrink-0" />
        <span>
          代码类（jar）扩展包在独立 ClassLoader 中运行，仅管理员可导入；包摘要 SHA-256：
          <code className="ml-1 break-all">{item.sha256 || '内置包无摘要'}</code>；所有工具调用写入审计表 ba_tool_invocations。
        </span>
      </div>
    </div>
  );
}
