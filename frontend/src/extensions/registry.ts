import type { RendererComponent } from './types';

/**
 * 前端渲染器注册表：渲染类型 → 渲染函数。
 *
 * 内置类型（chart / table / export / json）在 builtins 中注册，
 * 扩展包通过 activate() 动态注册自定义类型。
 */
const registry = new Map<string, RendererComponent>();
const listeners = new Set<() => void>();

export function registerRenderer(type: string, renderer: RendererComponent): void {
  registry.set(type, renderer);
  listeners.forEach((fn) => fn());
}

export function unregisterRenderer(type: string): void {
  registry.delete(type);
  listeners.forEach((fn) => fn());
}

export function getRenderer(type: string): RendererComponent | undefined {
  return registry.get(type);
}

export function listRenderers(): string[] {
  return Array.from(registry.keys());
}

export function subscribeRenderer(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}
