import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {api, url, type Row} from "./api";
import {useRouteRequest} from "./routeTransition";

type Slice = {records: Row[]; nextCursor: string | number | null; hasMore: boolean};
type Session = {
  id: string; owner: string; active: boolean; records: Row[]; latest: string | number | null; cursor: Slice["nextCursor"];
  more: boolean; loading: boolean; older: boolean; newer: boolean; error: string; syncAgain: boolean; syncError: string;
  unseen: Set<string>; following: boolean; controllers: Set<AbortController>;
};
export function mergeMessages(current: Row[], incoming: Row[]) {
  const unique = new Map(current.map(message => [String(message.id), message]));
  for (const message of incoming) unique.set(String(message.id), message);
  return [...unique.values()].sort((a, b) => BigInt(a.id) < BigInt(b.id) ? -1 : BigInt(a.id) > BigInt(b.id) ? 1 : 0);
}

export function usePrivateHistory(conversation: unknown, visible: boolean, actorId?: unknown) {
  const id = conversation == null ? "" : String(conversation);
  const viewport = useRef<HTMLDivElement>(null);
  const session = useRef<Session | null>(null);
  const anchor = useRef<{session: Session; height: number; top: number; messageId?: string; offset?: number} | "bottom" | null>(null);
  const sync = useRef<() => void>(() => {});
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
    const s: Session = {id, owner: String(actorId), active: true, records: [], latest: null, cursor: null, more: false, loading: true, older: false, newer: false, error: "", syncAgain: false, syncError: "", unseen: new Set(), following: true, controllers: new Set()};
    session.current = s; publish();
    void request(s).then(page => {
      if (!valid(s)) return;
      s.records = mergeMessages(s.records, page.records); s.latest = page.records[0]?.id ?? null; s.cursor = page.nextCursor; s.more = page.hasMore;
      anchor.current = "bottom";
    }).catch(error => { if (valid(s)) s.error = error.message; })
      .finally(() => { if (valid(s)) { s.loading = false; publish(); if (s.syncAgain) sync.current(); } });
  }, [id, actorId, publish, request]);

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
      if (element) {
        const top = element.getBoundingClientRect().top;
        const row = [...element.querySelectorAll<HTMLElement>("[data-message-id]")]
          .find(node => node.getBoundingClientRect().height > 0 && node.getBoundingClientRect().bottom > top);
        anchor.current = nearBottom() ? "bottom" : {session: s, height: element.scrollHeight, top: element.scrollTop,
          messageId: row?.dataset.messageId, offset: row ? row.getBoundingClientRect().top - top : undefined};
      }
      s.records = mergeMessages(s.records, page.records); s.cursor = page.nextCursor; s.more = page.hasMore;
    } catch (error) { if (valid(s)) s.error = (error as Error).message; }
    finally { if (valid(s)) { s.older = false; publish(); } }
  }, [publish, request]);

  const refreshLatest = useCallback(async () => {
    const s = session.current;
    if (!s || !valid(s)) return;
    if (s.loading || s.newer) { s.syncAgain = true; return; }
    s.newer = true; s.syncAgain = false;
    let succeeded = false;
    try {
      for (let n = 0; n < 20; n++) {
        const last = s.latest;
        const page = await request(s, last == null ? {} : {afterId: last});
        if (!valid(s)) return;
        if (s.syncError) { s.syncError = ""; publish(); }
        if (last == null && s.error) { s.error = ""; publish(); }
        if (page.records.length) {
          const follow = anchor.current === "bottom" || nearBottom();
          s.following = follow;
          if (follow) { anchor.current = "bottom"; s.unseen.clear(); }
          else {
            const known = new Set(s.records.map(message => String(message.id)));
            for (const message of page.records) {
              if (!known.has(String(message.id)) && String(message.fromUserId) !== String(actorId)) s.unseen.add(String(message.id));
            }
          }
          if (last == null) { s.cursor = page.nextCursor; s.more = page.hasMore; }
          s.records = mergeMessages(s.records, page.records);
          s.latest = last == null ? page.records[0].id : page.nextCursor; publish();
        }
        if (last == null || !page.hasMore || !page.records.length) break;
        if (s.latest === last) throw new Error("消息同步游标未前进");
        if (n === 19) s.syncAgain = true;
      }
      succeeded = true;
    } catch (error) { if (valid(s)) { s.syncError = (error as Error).message; publish(); } }
    finally {
      if (valid(s)) {
        s.newer = false;
        if (succeeded && s.syncAgain) queueMicrotask(() => { if (valid(s)) sync.current(); });
      }
    }
  }, [publish, request, actorId]);

  sync.current = () => { void refreshLatest(); };

  useEffect(() => {
    if (!id || !visible) return;
    const update = () => { if (document.visibilityState !== "hidden" && navigator.onLine !== false) void refreshLatest(); };
    window.addEventListener("server-message", update);
    window.addEventListener("message-connection-open", update);
    window.addEventListener("online", update);
    document.addEventListener("visibilitychange", update);
    const timer = window.setInterval(update, 5000);
    return () => {
      window.removeEventListener("server-message", update);
      window.removeEventListener("message-connection-open", update);
      window.removeEventListener("online", update);
      document.removeEventListener("visibilitychange", update); window.clearInterval(timer);
    };
  }, [id, visible, refreshLatest]);

  useLayoutEffect(() => {
    const element = viewport.current, pending = anchor.current;
    if (!element) return;
    if (pending === "bottom") { element.scrollTop = element.scrollHeight; if (session.current) session.current.following = true; }
    else if (pending && valid(pending.session)) {
      const row = pending.messageId && [...element.querySelectorAll<HTMLElement>("[data-message-id]")]
        .find(node => node.dataset.messageId === pending.messageId);
      if (row && pending.offset != null) element.scrollTop += row.getBoundingClientRect().top - element.getBoundingClientRect().top - pending.offset;
      else element.scrollTop = pending.top + element.scrollHeight - pending.height;
    }
    anchor.current = null;
    if (element.clientHeight > 0 && element.scrollHeight <= element.clientHeight && !session.current?.error) void loadOlder();
  });

  const jumpToLatest = () => {
    const s = session.current; if (!s || !valid(s)) return;
    s.unseen.clear(); s.following = true; anchor.current = "bottom"; publish();
  };
  const onScroll = () => {
    const s = session.current; if (!s || !valid(s)) return;
    s.following = nearBottom();
    if (s.following && s.unseen.size) { s.unseen.clear(); publish(); }
    if ((viewport.current?.scrollTop ?? Infinity) <= 48) void loadOlder();
  };
  // Follow viewport/content resizing only while the user is already at the bottom.
  useLayoutEffect(() => {
    const element = viewport.current, s = session.current;
    if (!element || !s || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => {
      if (valid(s) && s.following) element.scrollTop = element.scrollHeight;
    });
    observer.observe(element);
    const content = element.querySelector(".data-region"); if (content) observer.observe(content);
    return () => observer.disconnect();
  });

  const append = (message: Row) => {
    const s = session.current; if (!s || !valid(s) || s.id !== id) return;
    s.records = mergeMessages(s.records, [message]); s.unseen.clear(); s.following = true; anchor.current = "bottom"; publish();
  };
  const current = session.current?.id === id && session.current.owner === String(actorId) ? session.current : null;
  useRouteRequest(!!id && (!current || current.loading));
  return {
    data: {records: current?.records || []}, viewport, append, loadOlder, reload: reset,
    retrySync: refreshLatest, syncError: current?.syncError || "",
    onScroll, jumpToLatest, newMessageCount: current?.unseen.size || 0,
    loading: !!id && (!current || current.loading), refreshing: false,
    error: current?.records.length ? "" : current?.error || "",
    moreError: current?.records.length ? current.error : "",
    loadingOlder: current?.older || false, hasMore: current?.more || false,
  };
}
