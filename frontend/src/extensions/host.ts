import React from 'react';
import * as ReactDOM from 'react-dom';
import * as jsxRuntime from 'react/jsx-runtime';
import * as recharts from 'recharts';
import type { ExtensionHost } from './types';

/**
 * 宿主能力对象：通过 window.__EXT_HOST__ 暴露给扩展包，
 * 保证扩展包与宿主共享同一份 React / Recharts，避免双实例与 hooks 失效。
 */
const host: ExtensionHost = {
  React,
  ReactDOM,
  jsxRuntime,
  recharts,
  sdkVersion: '1.0.0',
};

declare global {
  interface Window {
    __EXT_HOST__?: ExtensionHost;
    __bizagentExt?: Record<string, { activate: (h: ExtensionHost) => unknown }>;
  }
}

window.__EXT_HOST__ = host;
window.__bizagentExt = window.__bizagentExt || {};

export default host;
