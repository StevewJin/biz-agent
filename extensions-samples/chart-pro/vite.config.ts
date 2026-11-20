import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * 扩展包前端构建：
 * - 输出 IIFE（entry.js），通过 window.__EXT_HOST__ 共享宿主的 React / Recharts
 * - 不打包 react/recharts，避免双实例
 * - 产物目录 ui/，由平台 /ext-assets/{code}/{version}/ui/entry.js 提供
 */
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'ui',
    emptyOutDir: true,
    lib: {
      entry: 'src/index.tsx',
      name: '__BIZAGENT_CHART_PRO__',
      formats: ['iife'],
      fileName: () => 'entry.js',
    },
    rollupOptions: {
      external: ['react', 'react-dom', 'react/jsx-runtime', 'recharts'],
      output: {
        globals: {
          react: '__EXT_HOST__.React',
          'react-dom': '__EXT_HOST__.ReactDOM',
          'react/jsx-runtime': '__EXT_HOST__.jsxRuntime',
          recharts: '__EXT_HOST__.recharts',
        },
      },
    },
  },
});
