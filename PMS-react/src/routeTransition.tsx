import React, {createContext, Suspense, useCallback, useContext, useEffect, useId, useLayoutEffect, useRef, useState} from 'react';

// Readiness belongs to one navigation, not a reusable page-data cache.
class RouteReadiness {
  requests = new Map<string, boolean>();
  mounted = false;
  done = false;
  frame: number | undefined;
  ready: () => void = () => {};
  update(id: string, pending: boolean) { this.requests.set(id, pending); this.schedule(); }
  remove(id: string) { this.requests.delete(id); this.schedule(); }
  schedule() {
    if (this.frame !== undefined) cancelAnimationFrame(this.frame);
    if (!this.mounted || this.done || [...this.requests.values()].some(Boolean)) return;
    // Allow data-dependent effects to start their own initial requests before reveal.
    this.frame = requestAnimationFrame(() => {
      this.frame = requestAnimationFrame(() => {
        this.frame = undefined;
        if (!this.mounted || this.done || [...this.requests.values()].some(Boolean)) return;
        this.done = true;
        this.ready();
      });
    });
  }
  dispose() { this.mounted = false; if (this.frame !== undefined) cancelAnimationFrame(this.frame); }
}
const RouteLoadContext = createContext<RouteReadiness | null>(null);
const RoutePathContext = createContext<string | null>(null);
const RouteVisibleContext = createContext(true);
export function useRouteVisible() { return useContext(RouteVisibleContext); }
export function useRouteLocation() { return useContext(RoutePathContext) ?? window.location.pathname + window.location.search; }
export function useRouteRequest(pending: boolean) {
  const scope = useContext(RouteLoadContext), id = useId();
  useLayoutEffect(() => {
    if (!scope) return;
    scope.update(id, pending);
    return () => scope.remove(id);
  }, [scope, id, pending]);
}
function Mounted({scope}: {scope: RouteReadiness}) {
  useLayoutEffect(() => { scope.mounted = true; scope.schedule(); return () => scope.dispose(); }, [scope]);
  return null;
}
type Entry = {id: number; path: string};
function RouteStage({entry, active, ready, children}: {entry: Entry; active: boolean; ready: (id: number) => void; children: React.ReactNode}) {
  const parentVisible = useContext(RouteVisibleContext);
  const [scope] = useState(() => new RouteReadiness());
  scope.ready = () => ready(entry.id);
  return <div className="route-stage" hidden={!active} inert={!active || undefined}>
    <RouteVisibleContext.Provider value={parentVisible && active}><RoutePathContext.Provider value={entry.path}><RouteLoadContext.Provider value={scope}>
      <Suspense fallback={null}>{children}<Mounted scope={scope} /></Suspense>
    </RouteLoadContext.Provider></RoutePathContext.Provider></RouteVisibleContext.Provider>
  </div>;
}
export function AtomicRoutes({path, onCommit, children}: {path: string; onCommit?: (path: string) => void; children: (path: string) => React.ReactNode}) {
  const visible = useContext(RouteVisibleContext);
  const sequence = useRef(0), requested = useRef(path);
  requested.current = path;
  const [routes, setRoutes] = useState<{active: Entry | null; pending: Entry | null}>(() => ({active: null, pending: {id: 0, path}}));
  useEffect(() => {
    setRoutes(previous => {
      if (previous.active?.path === path) return previous.pending ? {...previous, pending: null} : previous;
      if (previous.pending?.path === path) return previous;
      return {...previous, pending: {id: ++sequence.current, path}};
    });
  }, [path]);
  const ready = useCallback((id: number) => setRoutes(previous => {
    if (previous.pending?.id !== id || previous.pending.path !== requested.current) return previous;
    return {active: previous.pending, pending: null};
  }), []);
  useRouteRequest(!routes.active || routes.active.path !== path || !!routes.pending);
  useLayoutEffect(() => {
    if (!routes.active) return;
    onCommit?.(routes.active.path);
    if (visible) window.scrollTo({top: 0});
  }, [routes.active, onCommit, visible]);
  return <>{[routes.active, routes.pending].filter((entry): entry is Entry => !!entry).map(entry =>
    <RouteStage key={entry.id} entry={entry} active={routes.active?.id === entry.id} ready={ready}>{children(entry.path)}</RouteStage>
  )}</>;
}
