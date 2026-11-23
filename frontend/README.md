# frontend

**English** · [简体中文](README.zh-CN.md)

BizAgent frontend: chat UI + admin console + extension renderer host.

Stack: React 19 + TypeScript + Vite + Tailwind CSS + Zustand + Recharts.

## Development

```bash
npm install
npm run dev      # http://localhost:5173, /api and /ws proxied to 8080
```

## Build

```bash
npm run build    # outputs dist/
npm run lint     # oxlint
```

Production does not serve `dist/` separately: running `./build.sh` at the repository root copies the build into
`backend/src/main/resources/static/` and packages everything into a single fat jar.

## Layout

| Path | Description |
|---|---|
| `src/pages` | Pages (chat, skill marketplace, admin modules) |
| `src/components/admin/ui.tsx` | Admin design system (PageHeader / Card / Button / Modal / Alert, …) |
| `src/components/common/feedback.tsx` | Global toast and confirm dialog |
| `src/extensions` | Frontend extension host: `window.__EXT_HOST__` shared deps, renderer registry, `_render` protocol |
| `src/stores` | Zustand stores |
| `src/services` | REST / WebSocket clients |

For writing extension frontend renderers, see [../docs/extension-authoring.md](../docs/extension-authoring.md).
