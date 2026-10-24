import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/pet-system": {
        target: process.env.PMS_API_TARGET || "http://111.229.254.252",
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: { sourcemap: false },
});
