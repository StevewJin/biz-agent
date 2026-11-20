# frontend

BizAgent 前端：对话界面 + 管理控制台 + 扩展渲染器宿主。

技术栈：React 19 + TypeScript + Vite + Tailwind CSS + Zustand + Recharts。

## 开发

```bash
npm install
npm run dev      # http://localhost:5173，/api 与 /ws 已代理到 8080
```

## 构建

```bash
npm run build    # 产出 dist/
npm run lint     # oxlint
```

生产部署不需要单独托管 `dist/`：根目录执行 `./build.sh` 会把前端产物复制到
`backend/src/main/resources/static/` 并打成单个 fat jar。

## 目录说明

| 目录 | 说明 |
|---|---|
| `src/pages` | 页面（对话、技能市场、管理控制台各模块） |
| `src/components/admin/ui.tsx` | 管理端设计系统组件（PageHeader / Card / Button / Modal / Alert 等） |
| `src/components/common/feedback.tsx` | 全局 Toast 与确认对话框 |
| `src/extensions` | 前端扩展宿主：`window.__EXT_HOST__` 共享依赖、渲染器注册表、`_render` 协议 |
| `src/stores` | Zustand 状态 |
| `src/services` | REST / WebSocket 客户端 |

扩展包的前端渲染器如何编写，见 [../docs/extension-authoring.md](../docs/extension-authoring.md)。
