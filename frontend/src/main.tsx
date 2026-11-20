import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './extensions/host'
import { registerBuiltinRenderers } from './extensions/builtins'
import App from './App'

// 注册内置渲染器（chart / table / export / json），扩展包渲染器按需动态加载
registerBuiltinRenderers()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
