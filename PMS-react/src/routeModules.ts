import { moduleLoader } from "./moduleLoader.ts";

export const loadAdmin = moduleLoader(() => import("./admin"));
export const loadChat = moduleLoader(() => import("./chat"));
export const loadCharts = moduleLoader(() => import("./charts"));

export function preloadRoute(path: string) {
  const page = path.split(/[?#]/)[0];
  const load = /^\/audit(?:\/|$)/.test(page)
    ? loadAdmin
    : page === "/private-message" || page === "/ai-chat"
      ? loadChat
      : null;
  if (load) void load().catch(() => {});
}

/** Defer warm-up until idle and avoid speculative traffic on constrained links. */
export function warmRouteModules(admin: boolean) {
  const connection = (
    navigator as Navigator & { connection?: { saveData?: boolean; effectiveType?: string } }
  ).connection;
  if (connection?.saveData || /(^|-)2g$/.test(connection?.effectiveType || "")) return () => {};
  const warm = () => {
    preloadRoute("/private-message");
    if (admin) preloadRoute("/audit");
  };
  const idle = window.requestIdleCallback as
    ((callback: IdleRequestCallback, options?: IdleRequestOptions) => number) | undefined;
  if (typeof idle === "function") {
    const id = idle.call(window, warm, { timeout: 3000 });
    return () => window.cancelIdleCallback(id);
  }
  const id = window.setTimeout(warm, 1500);
  return () => window.clearTimeout(id);
}
