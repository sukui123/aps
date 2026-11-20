import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 开发期把 /api 反向代理到 aps 单进程（技术方案 §2.3/§8.5）
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
