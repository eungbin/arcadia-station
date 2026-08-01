import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// vite.config는 Node에서 실행되지만 이 프로젝트는 @types/node를 의존하지 않는다.
// loadEnv에 필요한 최소 형태만 선언한다.
declare const process: { cwd(): string };

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");

  return {
    plugins: [react()],
    server: {
      host: "127.0.0.1",
      port: 5173,
      // 게임 백엔드에 CORS 설정이 없다. 개발에서는 같은 출처로 보이도록 `/api`를 프록시한다.
      proxy: {
        "/api": {
          target: env.VITE_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
          changeOrigin: true,
        },
      },
    },
    build: {
      target: "es2022",
      sourcemap: true,
    },
  };
});
