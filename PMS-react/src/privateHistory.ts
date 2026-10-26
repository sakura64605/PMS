import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {api, url, type Row} from "./api";
import {useRouteRequest} from "./routeTransition";

type Slice = {records: Row[]; nextCursor: string | number | null; hasMore: boolean};
type Session = {
  id: string; active: boolean; records: Row[]; latest: string | number | null; cursor: Slice["nextCursor"];
  more: boolean; loading: boolean; older: boolean; newer: boolean; error: string;
  controllers: Set<AbortController>;
};
export function mergeMessages(current: Row[], incoming: Row[]) {
  const unique = new Map(current.map(message => [String(message.id), message]));
  for (const message of incoming) unique.set(String(message.id), message);
  return [...unique.values()].sort((a, b) => BigInt(a.id) < BigInt(b.id) ? -1 : BigInt(a.id) > BigInt(b.id) ? 1 : 0);
}

export function usePrivateHistory(conversation: unknown, visible: boolean) {
  const id = conversation == null ? "" : String(conversation);
  const viewport = useRef<HTMLDivElement>(null);
  const session = useRef<Session | null>(null);
  const anchor = useRef<{session: Session; height: number; top: number} | "bottom" | null>(null);
  const [, render] = useState(0);
  const publish = useCallback(() => render(n => n + 1), []);
  const request = useCallback(async (s: Session, params: Row = {}) => {
    const controller = new AbortController(); s.controllers.add(controller);
    try {
      return await api<Slice>(url("/message/private/messages", {conversationId: s.id, pageSize: 30, ...params}), "GET", undefined, controller.signal);
    } finally { s.controllers.delete(controller); }
  }, []);
  const valid = (s: Session) => s.active && session.current === s;
  const nearBottom = () => !viewport.current || viewport.current.scrollHeight - viewport.current.scrollTop - viewport.current.clientHeight < 64;

  const reset = useCallback(() => {
    if (session.current) { session.current.active = false; session.current.controllers.forEach(c => c.abort()); }
    anchor.current = null;
    if (!id) { session.current = null; publish(); return; }
    const s: Session = {id, active: true, records: [], latest: null, cursor: null, more: false, loading: true, older: false, newer: false, error: "", controllers: new Set()};
    session.current = s; publish();
    void request(s).then(page => {
      if (!valid(s)) return;
      s.records = mergeMessages(s.records, page.records); s.latest = page.records[0]?.id ?? null; s.cursor = page.nextCursor; s.more = page.hasMore;
      anchor.current = "bottom";
    }).catch(error => { if (valid(s)) s.error = error.message; })
      .finally(() => { if (valid(s)) { s.loading = false; publish(); } });
  }, [id, publish, request]);

  useEffect(() => {
    reset();
    return () => { const s = session.current; if (s) { s.active = false; s.controllers.forEach(c => c.abort()); } };
  }, [reset]);

  const loadOlder = useCallback(async () => {
    const s = session.current;
    if (!s || !valid(s) || s.loading || s.older || !s.more || s.cursor == null) return;
    s.older = true; s.error = ""; publish();
    try {
      const page = await request(s, {beforeId: s.cursor});
      if (!valid(s)) return;
      const element = viewport.current;
      if (element) anchor.current = {session: s, height: element.scrollHeight, top: element.scrollTop};
      s.records = mergeMessages(s.records, page.records); s.cursor = page.nextCursor; s.more = page.hasMore;
    } catch (error) { if (valid(s)) s.error = (error as Error).message; }
    finally { if (valid(s)) { s.older = false; publish(); } }
  }, [publish, request]);

  const refreshLatest = useCallback(async () => {
    const s = session.current;
    if (!s || !valid(s) || s.loading || s.newer) return;
    s.newer = true;
    try {
      for (let n = 0; n < 20; n++) {
        const last = s.latest;
        const page = await request(s, last == null ? {} : {afterId: last});
        if (!valid(s)) return;
        if (page.records.length) {
          if (nearBottom()) anchor.current = "bottom";
          if (last == null) { s.cursor = page.nextCursor; s.more = page.hasMore; }
          s.records = mergeMessages(s.records, page.records);
          s.latest = last == null ? page.records[0].id : page.nextCursor; publish();
        }
        if (last == null || !page.hasMore || !page.records.length) break;
      }
    } catch { /* Keep the current history; retry on the next push or poll. */ }
    finally { if (valid(s)) s.newer = false; }
  }, [publish, request]);

  useEffect(() => {
    if (!id || !visible) return;
    const update = () => { if (document.visibilityState !== "hidden") void refreshLatest(); };
    window.addEventListener("server-message", update);
    const timer = window.setInterval(update, 5000);
    return () => { window.removeEventListener("server-message", update); window.clearInterval(timer); };
  }, [id, visible, refreshLatest]);

  useLayoutEffect(() => {
    const element = viewport.current, pending = anchor.current;
    if (!element) return;
    if (pending === "bottom") element.scrollTop = element.scrollHeight;
    else if (pending && valid(pending.session)) element.scrollTop = pending.top + element.scrollHeight - pending.height;
    anchor.current = null;
    if (element.clientHeight > 0 && element.scrollHeight <= element.clientHeight && !session.current?.error) void loadOlder();
  });

  const append = (message: Row) => {
    const s = session.current; if (!s || !valid(s) || s.id !== id) return;
    s.records = mergeMessages(s.records, [message]); anchor.current = "bottom"; publish();
  };
  const current = session.current?.id === id ? session.current : null;
  useRouteRequest(!!id && (!current || current.loading));
  return {
    data: {records: current?.records || []}, viewport, append, loadOlder, reload: reset,
    loading: !!id && (!current || current.loading), refreshing: false,
    error: current?.records.length ? "" : current?.error || "",
    moreError: current?.records.length ? current.error : "",
    loadingOlder: current?.older || false, hasMore: current?.more || false,
  };
}
