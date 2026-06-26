import { defineConfig, Plugin } from "vite";
import react from "@vitejs/plugin-react";

/** 允许 vendor POC（5174）以 iframe 嵌入银行 PIN 页；生产应改为银行白名单父域。 */
function frameAncestorsCsp(allowedParents: string[]): Plugin {
  const v = ["'self'", ...allowedParents].join(" ");
  return {
    name: "frame-ancestors-csp",
    configureServer(server) {
      server.middlewares.use((_req, res, next) => {
        res.setHeader("Content-Security-Policy", `frame-ancestors ${v}`);
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [
    react(),
    frameAncestorsCsp(["http://localhost:5174", "http://127.0.0.1:5174"]),
  ],
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      "/api": { target: "http://127.0.0.1:8080", changeOrigin: true },
    },
  },
});
