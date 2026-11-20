import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { getRenderer } from '../../extensions/registry';
import { ensureRendererLoaded } from '../../extensions/loader';
import type { RenderDescriptor } from '../../extensions/types';

interface ExtensionRendererProps {
  /** 后端下发的渲染负载 { type, props } */
  render?: RenderDescriptor | null;
  /** 降级展示内容 */
  fallback?: ReactNode;
}

/**
 * 扩展包渲染入口。
 *
 * 按渲染类型查注册表：内置类型直接渲染；扩展包类型按需加载其入口脚本后渲染；
 * 加载失败或类型未注册时降级为 JSON 折叠展示，避免白屏。
 */
export default function ExtensionRenderer({ render, fallback }: ExtensionRendererProps) {
  const [, forceUpdate] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const renderType = render?.type;

  useEffect(() => {
    if (!renderType || getRenderer(renderType)) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    ensureRendererLoaded(renderType)
      .then((ok) => {
        if (cancelled) return;
        setLoading(false);
        if (ok) forceUpdate((v) => v + 1);
        else setError(`未找到渲染器: ${renderType}`);
      })
      .catch(() => {
        if (!cancelled) {
          setLoading(false);
          setError(`渲染器加载失败: ${renderType}`);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [renderType]);

  if (!render) {
    return <>{fallback ?? null}</>;
  }

  const renderer = getRenderer(render.type);
  if (renderer) {
    try {
      return <div className="my-2">{renderer(render.props) as ReactNode}</div>;
    } catch (e) {
      console.error('[extensions] 渲染异常', render.type, e);
      return <FallbackJson render={render} note="渲染异常，已降级展示" />;
    }
  }

  if (loading) {
    return <div className="text-xs text-gray-400 my-2">正在加载渲染器 {render.type} ...</div>;
  }

  return <FallbackJson render={render} note={error ?? '未注册的渲染类型'} />;
}

function FallbackJson({ render, note }: { render: RenderDescriptor; note: string }) {
  return (
    <div className="my-2">
      <div className="text-[11px] text-gray-400 mb-1">{note}</div>
      <pre className="text-xs bg-white/60 border border-gray-200 rounded p-2 overflow-x-auto whitespace-pre-wrap break-all text-gray-600 max-h-64 overflow-y-auto">
        {JSON.stringify(render.props, null, 2)}
      </pre>
    </div>
  );
}
