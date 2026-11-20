/**
 * 扩展包前端运行时类型定义。
 *
 * 扩展包入口文件（entry.js）需注册到全局：
 *   (window.__bizagentExt = window.__bizagentExt || {})['<extCode>'] = { activate }
 *
 * activate(host) 返回 { renderers: [{ type, render }] }。
 */
export interface ExtensionHost {
  React: typeof import('react');
  ReactDOM: typeof import('react-dom');
  jsxRuntime: typeof import('react/jsx-runtime');
  recharts: typeof import('recharts');
  sdkVersion: string;
}

export interface RenderDescriptor {
  /** 渲染类型，例如 chart / table / chart_pro.chart */
  type: string;
  /** 渲染参数 */
  props: Record<string, unknown>;
}

export type RendererComponent = (props: Record<string, unknown>) => unknown;

export interface ActivateResult {
  renderers?: Array<{ type: string; render: RendererComponent }>;
}

export interface ExtensionEntry {
  activate: (host: ExtensionHost) => ActivateResult | void;
}

export interface RendererCatalogItem {
  renderType: string;
  extCode: string;
  version: string;
  entry: string;
  enabled: boolean;
}
