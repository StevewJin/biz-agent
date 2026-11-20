import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  Wrench, ChevronDown, ChevronRight, CheckCircle2, Loader2, XCircle, Clock,
  Database, Cloud, BarChart3, FileDown, Code2, Puzzle, Sparkles,
} from 'lucide-react';
import type { RenderDescriptor } from '../../extensions/types';
import ExtensionRenderer from '../extensions/ExtensionRenderer';

interface ToolCallCardProps {
  toolName: string;
  /** 工具展示名（扩展包 manifest 中的 displayName） */
  toolDisplayName?: string | null;
  /** 所属扩展包 */
  extensionName?: string | null;
  extensionCode?: string | null;
  /** 工具类型：sql / http / render / export / jar */
  toolKind?: string | null;
  /** 触发该工具的技能包 */
  skillName?: string | null;
  toolInput: Record<string, unknown> | null;
  toolResult?: string | null;
  /** 后端下发的 _render 协议 JSON 字符串 */
  render?: string | null;
  status: 'executing' | 'done';
  durationMs?: number | null;
  /** 是否在卡片内渲染（消息级已统一渲染时设为 false，避免重复） */
  showRender?: boolean;
}

const KIND_META: Record<string, { label: string; icon: typeof Database }> = {
  sql: { label: 'SQL 查询', icon: Database },
  http: { label: '接口调用', icon: Cloud },
  render: { label: '渲染输出', icon: BarChart3 },
  export: { label: '文件导出', icon: FileDown },
  jar: { label: '代码插件', icon: Code2 },
};

/** 工具键兜底展示：builtin_db__query → query */
function codeOf(toolName: string): string {
  const index = toolName.indexOf('__');
  return index >= 0 ? toolName.slice(index + 2) : toolName;
}

/** 解析 _render 协议 */
function parseRender(raw?: string | null): RenderDescriptor | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    const payload = parsed?._render;
    if (payload && typeof payload.type === 'string') {
      return { type: payload.type, props: payload.props || {} };
    }
  } catch {
    // 非渲染负载
  }
  return null;
}

export default function ToolCallCard({
  toolName,
  toolDisplayName,
  extensionName,
  extensionCode,
  toolKind,
  skillName,
  toolInput,
  toolResult,
  render,
  status,
  durationMs,
  showRender = true,
}: ToolCallCardProps) {
  const [expanded, setExpanded] = useState(false);
  const isError = toolResult?.startsWith('Error:') || toolResult?.startsWith('错误');
  const renderPayload = showRender ? parseRender(render) : null;

  const kind = toolKind ? KIND_META[toolKind] : undefined;
  const KindIcon = kind?.icon ?? Wrench;
  const title = toolDisplayName || codeOf(toolName);
  const extLabel = extensionName || extensionCode;

  return (
    <div className={`my-2 border rounded-lg overflow-hidden text-sm ${
      isError ? 'border-red-200 bg-red-50' : 'border-purple-200 bg-purple-50'
    }`}>
      {/* 头部 */}
      <button
        className="w-full flex items-start gap-2 px-3 py-2 hover:bg-white/50 transition-colors text-left"
        onClick={() => setExpanded(!expanded)}
      >
        <span className="mt-0.5 shrink-0">
          {status === 'executing' ? (
            <Loader2 className="w-4 h-4 text-purple-500 animate-spin" />
          ) : isError ? (
            <XCircle className="w-4 h-4 text-red-500" />
          ) : (
            <CheckCircle2 className="w-4 h-4 text-green-500" />
          )}
        </span>

        <span className="min-w-0 flex-1">
          {/* 第一行：具体工具名 + 类型 + 扩展包 */}
          <span className="flex flex-wrap items-center gap-1.5">
            <span className="font-medium text-gray-800">{title}</span>
            {toolKind && (
              <span className="inline-flex items-center gap-0.5 rounded bg-white/80 px-1.5 py-0.5 text-[10px] font-medium text-purple-600 ring-1 ring-purple-100">
                <KindIcon className="h-3 w-3" />
                {kind?.label || toolKind}
              </span>
            )}
            {extLabel && (
              <span className="inline-flex items-center gap-0.5 rounded bg-white/80 px-1.5 py-0.5 text-[10px] text-gray-500 ring-1 ring-gray-100">
                <Puzzle className="h-3 w-3" />
                {extLabel}
              </span>
            )}
            {status === 'executing' && (
              <span className="text-[11px] text-purple-500">执行中…</span>
            )}
          </span>

          {/* 第二行：技能归属 + 工具键 */}
          <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[11px] text-gray-500">
            {skillName && (
              <span className="inline-flex items-center gap-0.5">
                <Sparkles className="h-3 w-3 text-amber-500" />
                技能 · {skillName}
              </span>
            )}
            <code className="text-gray-400">{toolName}</code>
          </span>
        </span>

        {durationMs != null && (
          <span className="mt-0.5 shrink-0 text-xs text-gray-400 flex items-center gap-1">
            <Clock className="w-3 h-3" />
            {(durationMs / 1000).toFixed(1)}s
          </span>
        )}
        {status === 'done' && (
          <span className="mt-0.5 shrink-0">
            {expanded
              ? <ChevronDown className="w-4 h-4 text-gray-400" />
              : <ChevronRight className="w-4 h-4 text-gray-400" />}
          </span>
        )}
      </button>

      {/* 渲染区（图表 / 表格 / 导出 / 扩展包自定义渲染） */}
      {renderPayload && (
        <div className="px-3 pb-3 border-t border-purple-100 pt-2 bg-white/40">
          <ExtensionRenderer render={renderPayload} />
        </div>
      )}

      {/* 展开区 (参数 + 原始结果) */}
      {expanded && (
        <div className="px-3 pb-3 space-y-2 border-t border-purple-100 pt-2">
          {toolInput && Object.keys(toolInput).length > 0 && (
            <div>
              <div className="text-xs text-gray-400 mb-1">参数</div>
              <pre className="text-xs bg-white/60 rounded p-2 overflow-x-auto whitespace-pre-wrap break-all text-gray-600 max-h-32 overflow-y-auto">
                {JSON.stringify(toolInput, null, 2)}
              </pre>
            </div>
          )}

          {toolResult != null && (
            <div>
              <div className="text-xs text-gray-400 mb-1">
                {isError ? '错误' : (renderPayload ? '原始数据' : '结果')}
              </div>
              <div className={`text-xs rounded p-2 overflow-x-auto max-h-48 overflow-y-auto prose prose-xs max-w-none ${
                isError ? 'bg-red-100 text-red-700' : 'bg-white/60 text-gray-600'
              }`}>
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {toolResult}
                </ReactMarkdown>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
