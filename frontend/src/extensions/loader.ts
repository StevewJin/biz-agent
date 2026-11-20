import host from './host';
import { getRenderer, registerRenderer } from './registry';
import type { ExtensionEntry, RendererCatalogItem } from './types';
import { getExtensionRenderers } from '../services/api';

/**
 * 扩展包前端加载器。
 *
 * 加载流程：
 * 1. 从 /api/extensions/renderers 获取「渲染类型 → 扩展包」映射（缓存）
 * 2. 注入 <script src="/ext-assets/{code}/{version}/{entry}">
 * 3. 读取 window.__bizagentExt[code].activate(host)，注册其渲染器
 *
 * 同一 code@version 只会加载一次；失败后记录错误，避免无限重试。
 */
const packLoads = new Map<string, Promise<void>>();
const packErrors = new Map<string, string>();
let catalogPromise: Promise<Record<string, RendererCatalogItem>> | null = null;

async function loadCatalog(): Promise<Record<string, RendererCatalogItem>> {
  if (!catalogPromise) {
    catalogPromise = getExtensionRenderers()
      .then((res) => {
        const map: Record<string, RendererCatalogItem> = {};
        for (const item of res.data || []) {
          map[item.renderType] = item;
        }
        return map;
      })
      .catch(() => {
        catalogPromise = null;
        return {} as Record<string, RendererCatalogItem>;
      });
  }
  return catalogPromise;
}

function resolveAssetUrl(code: string, version: string, entry: string): string {
  const normalized = entry.startsWith('/') ? entry.slice(1) : entry;
  return `/ext-assets/${code}/${version}/${normalized}`;
}

function loadScript(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[data-ext-src="${url}"]`);
    if (existing) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.dataset.extSrc = url;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`扩展包脚本加载失败: ${url}`));
    document.head.appendChild(script);
  });
}

/** 加载指定扩展包并注册其渲染器 */
export function loadPack(code: string, version: string): Promise<void> {
  const key = `${code}@${version}`;
  const cached = packLoads.get(key);
  if (cached) return cached;

  const task = (async () => {
    const catalog = await loadCatalog();
    const item = Object.values(catalog).find((entry) => entry.extCode === code);
    if (!item || !item.entry) {
      throw new Error(`扩展包 ${code} 未声明前端入口`);
    }
    const url = resolveAssetUrl(code, version, item.entry);
    await loadScript(url);

    const entry = window.__bizagentExt?.[code] as ExtensionEntry | undefined;
    if (!entry || typeof entry.activate !== 'function') {
      throw new Error(`扩展包 ${code} 未正确注册 activate()`);
    }
    const result = entry.activate(host);
    for (const renderer of result?.renderers || []) {
      registerRenderer(renderer.type, renderer.render);
    }
  })().catch((error: Error) => {
    packErrors.set(key, error.message);
    console.error('[extensions] 扩展包加载失败', code, error);
    throw error;
  });

  packLoads.set(key, task);
  return task;
}

/**
 * 确保某个渲染类型可用：内置类型直接命中；
 * 形如 `pack.xxx` 的类型会按需加载对应扩展包。
 */
export async function ensureRendererLoaded(renderType: string): Promise<boolean> {
  if (getRenderer(renderType)) return true;

  const catalog = await loadCatalog();
  const item = catalog[renderType];
  if (!item) return false;
  if (packErrors.has(`${item.extCode}@${item.version}`)) return false;

  try {
    await loadPack(item.extCode, item.version);
  } catch {
    return false;
  }
  return Boolean(getRenderer(renderType));
}

/** 重置缓存（扩展包启停后调用） */
export function resetExtensionLoader(): void {
  catalogPromise = null;
  packErrors.clear();
}
