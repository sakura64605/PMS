import React, {
  createContext,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
} from "react";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronLeft,
  ChevronRight,
  Heart,
  LoaderCircle,
  PawPrint,
  RotateCcw,
  Search,
  X,
} from "lucide-react";
import { api, type Row, nick, rows, time } from "./api";
import { queryCache, queryKey } from "./queryCache.ts";
export const AppContext = createContext<{
  user: Row | null;
  refreshUser: () => Promise<void>;
  notify: (s: string, error?: boolean) => void;
  revision: number;
  refresh: () => void;
}>({
  user: null,
  refreshUser: async () => {},
  notify: () => {},
  revision: 0,
  refresh: () => {},
});
export const useApp = () => useContext(AppContext);
export function navigate(path: string) {
  window.history.pushState({}, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
  window.scrollTo({ top: 0 });
}
export function Link({
  to,
  children,
  ...props
}: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to: string }) {
  return (
    <a
      href={to}
      {...props}
      onClick={(e) => {
        props.onClick?.(e);
        if (
          !e.defaultPrevented &&
          !e.metaKey &&
          !e.ctrlKey &&
          !e.shiftKey &&
          e.button === 0
        ) {
          e.preventDefault();
          navigate(to);
        }
      }}
    >
      {children}
    </a>
  );
}
export function usePath() {
  const [path, set] = useState(location.pathname + location.search);
  useEffect(() => {
    const fn = () => set(location.pathname + location.search);
    window.addEventListener("popstate", fn);
    return () => window.removeEventListener("popstate", fn);
  }, []);
  return path;
}
export function useData<T = any>(path: string | null, key = 0) {
  const { revision } = useApp();
  const session = localStorage.getItem('token') || 'guest';
  const cacheKey = path ? queryKey(session, path) : '';
  const cached = path ? queryCache.get(cacheKey) : undefined;
  const [state, setState] = useState<{key: string; data: T | null; error: string; pending: boolean}>(() => ({key: cacheKey, data: cached?.data ?? null, error: '', pending: !!path && !cached}));
  const [local, reload] = useState(0);
  // Unread-count/read actions must not invalidate unrelated forms and details.
  const revisionKey = path?.startsWith('/message/') || path?.startsWith('/feed/') ? revision : 0;
  const previous = useRef({cacheKey: '', local: -1, revisionKey: -1, key: -1});
  const data = state.key === cacheKey ? state.data : cached?.data ?? null;
  useEffect(() => {
    if (!path) { setState({key: cacheKey, data: null, error: '', pending: false}); return; }
    const prior = previous.current;
    const forced = prior.cacheKey === cacheKey && (prior.local !== local || prior.revisionKey !== revisionKey || prior.key !== key);
    previous.current = {cacheKey, local, revisionKey, key};
    const snapshot = queryCache.get(cacheKey);
    if (!forced && snapshot && queryCache.fresh(snapshot)) {
      setState({key: cacheKey, data: snapshot.data, error: '', pending: false});
      return;
    }
    const controller = new AbortController();
    setState(prev => ({key: cacheKey, data: prev.key === cacheKey ? prev.data : snapshot?.data ?? null, error: '', pending: true}));
    api<T>(path, 'GET', undefined, controller.signal).then(value => {
      if (controller.signal.aborted) return;
      queryCache.put(cacheKey, path, value);
      setState({key: cacheKey, data: value, error: '', pending: false});
    }).catch(error => {
      if (!controller.signal.aborted) setState(prev => prev.key === cacheKey ? {...prev, error: error.message, pending: false} : prev);
    });
    return () => controller.abort();
  }, [cacheKey, path, key, local, revisionKey]);
  return {
    data: data as T | null,
    error: state.key === cacheKey ? state.error : '',
    loading: !!path && data == null && (state.key !== cacheKey || state.pending),
    refreshing: state.key === cacheKey && state.pending,
    reload: () => reload(v => v + 1),
    update: (updater: (old: T) => T) => {
      setState(prev => {
        if (prev.key !== cacheKey || prev.data == null || !path) return prev;
        const next = updater(prev.data);
        queryCache.put(cacheKey, path, next);
        return {...prev, data: next, error: ''};
      });
    },
  };
}
export function Button({
  children,
  action,
  tone = "",
  ...props
}: Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "onClick"> & {
  action?: () => unknown;
  tone?: string;
}) {
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const { notify } = useApp();
  return (
    <button
      {...props}
      type={props.type || "button"}
      className={`btn ${tone} ${props.className || ""}`}
      disabled={busy || props.disabled}
      aria-busy={busy}
      onClick={async () => {
        if (!action || lock.current) return;
        lock.current = true;
        setBusy(true);
        try {
          await action();
        } catch (e: any) {
          notify(e.message || "操作失败", true);
        } finally {
          lock.current = false;
          setBusy(false);
        }
      }}
    >
      {busy ? <LoaderCircle size={16} className="spin" /> : null}
      {children}
    </button>
  );
}
type SkeletonKind = 'rows' | 'cards' | 'detail' | 'chat' | 'table';
export function PageSkeleton({kind = 'rows'}: {kind?: SkeletonKind}) {
  const lines = <><i className="skeleton-bar skeleton-title" /><i className="skeleton-bar" /><i className="skeleton-bar skeleton-short" /></>;
  const row = <div className="skeleton-row"><i className="skeleton-avatar" /><div>{lines}</div></div>;
  return <div className={`page-skeleton skeleton-${kind}`} role="status" aria-label="正在加载页面内容" aria-busy="true">
    <span className="sr-only">正在加载页面内容</span>
    <div aria-hidden="true">
      {kind === 'cards' ? <div className="card-grid">{Array.from({length: 6}, (_, i) => <div className="pet-card" key={i}><div className="skeleton-cover" /><div className="skeleton-card-body">{lines}{row}</div></div>)}</div>
      : kind === 'detail' ? <div className="detail-layout"><article className="panel">{row}<div className="skeleton-heading">{lines}</div><div className="skeleton-photo" /><div className="skeleton-facts">{lines}{lines}</div>{lines}</article><aside className="panel">{row}<div className="skeleton-heading">{lines}</div><i className="skeleton-button" /></aside></div>
      : kind === 'chat' ? <div className="panel skeleton-chat">{row}<div className="skeleton-bubbles">{Array.from({length: 3}, (_, i) => <div className="skeleton-bubble" key={i}>{lines}</div>)}</div><i className="skeleton-button" /></div>
      : <div className="panel">{Array.from({length: kind === 'table' ? 7 : 4}, (_, i) => <div className="skeleton-list-item" key={i}>{row}</div>)}</div>}
    </div>
  </div>;
}
export function Busy() {
  const path = window.location.pathname;
  const kind: SkeletonKind = /private-message|ai-chat/.test(path) ? 'chat' : /^\/audit/.test(path) ? 'table' : /^\/pets\/(?:activity\/)?\d+/.test(path) ? 'detail' : path === '/' || path === '/pets' ? 'cards' : 'rows';
  return <PageSkeleton kind={kind} />;
}
export function Empty({
  text = "这里还没有内容",
  children,
}: {
  text?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="state">
      <PawPrint size={35} />
      <h3>{text}</h3>
      <p>每一份分享，都让陪伴更温暖。</p>
      {children}
    </div>
  );
}
export function DataState({
  request,
  children,
  empty = false,
  skeleton = 'rows',
}: {
  request: ReturnType<typeof useData>;
  children: React.ReactNode;
  empty?: boolean;
  skeleton?: SkeletonKind;
}) {
  if (request.loading) return <PageSkeleton kind={skeleton} />;
  if (request.error && request.data == null)
    return (
      <div className="state" role="alert">
        <p>{request.error}</p>
        <Button action={request.reload}>
          <RotateCcw size={15} />
          重新加载
        </Button>
      </div>
    );
  if (empty) return <Empty />;
  return <>{request.error && <div className="background-error" role="status">更新失败，暂时保留当前内容。<Button tone="ghost small" action={request.reload}>重试</Button></div>}{children}</>;
}
export function Intro({
  title,
  description,
  children,
  eyebrow = "PETCIRCLE COMMUNITY",
}: {
  title: string;
  description?: string;
  children?: React.ReactNode;
  eyebrow?: string;
}) {
  return (
    <section className="intro">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description && <p className="muted">{description}</p>}
      </div>
      <div className="intro-actions">{children}</div>
    </section>
  );
}
export function Avatar({
  user,
  size = 38,
}: {
  user?: Row | null;
  size?: number;
}) {
  const [failed, setFailed] = useState(false);
  return (
    <span className="avatar" style={{ width: size, height: size }}>
      {user?.avatar && !failed ? (
        <img src={user.avatar} alt="" onError={() => setFailed(true)} />
      ) : (
        nick(user).slice(0, 1)
      )}
    </span>
  );
}
export function Author({ user, date }: { user?: Row | null; date?: any }) {
  return (
    <div className="author">
      <Link to={`/user/${user?.id || user?.userId || ""}`}>
        <Avatar user={user} />
      </Link>
      <div>
        <Link
          to={`/user/${user?.id || user?.userId || ""}`}
          className="author-name"
        >
          {nick(user)}
        </Link>
        <small>{time(date)}</small>
      </div>
    </div>
  );
}
export function Badge({
  children,
  tone = "",
}: {
  children: React.ReactNode;
  tone?: string;
}) {
  return <span className={`badge ${tone}`}>{children}</span>;
}
export function Field({
  label,
  children,
  required = false,
}: {
  label: string;
  children: React.ReactNode;
  required?: boolean;
}) {
  const id = useId();
  return (
    <label className="field" htmlFor={id}>
      <span>
        {label}
        {required && <b aria-hidden="true"> *</b>}
      </span>
      {React.isValidElement(children)
        ? React.cloneElement(children as React.ReactElement<any>, { id })
        : children}
    </label>
  );
}
export function Pagination({
  page,
  total,
  size = 12,
  onChange,
}: {
  page: number;
  total: number;
  size?: number;
  onChange: (p: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <nav className="pagination" aria-label="分页">
      <span>共 {total} 条</span>
      <Button
        disabled={page <= 1}
        action={() => onChange(page - 1)}
        aria-label="上一页"
      >
        <ChevronLeft size={16} />
      </Button>
      <span>
        {page} / {pages}
      </span>
      <Button
        disabled={page >= pages}
        action={() => onChange(page + 1)}
        aria-label="下一页"
      >
        <ChevronRight size={16} />
      </Button>
    </nav>
  );
}
export function SearchForm({
  onSearch,
  children,
  placeholder = "搜索标题、内容…",
}: {
  onSearch: (s: string) => void;
  children?: React.ReactNode;
  placeholder?: string;
}) {
  const [text, set] = useState("");
  return (
    <form
      className="toolbar"
      onSubmit={(e) => {
        e.preventDefault();
        onSearch(text.trim());
      }}
    >
      <div className="search-input">
        <Search size={17} />
        <input
          aria-label={placeholder}
          placeholder={placeholder}
          value={text}
          onChange={(e) => set(e.target.value)}
        />
      </div>
      {children}
      <Button type="submit" tone="primary">
        搜索
      </Button>
      <Button
        action={() => {
          set("");
          onSearch("");
        }}
        aria-label="重置搜索"
      >
        <RotateCcw size={16} />
        重置
      </Button>
    </form>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null),
    id = useId();
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={id}
      className="modal"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current) {
          const r = ref.current.getBoundingClientRect();
          if (
            e.clientX < r.left ||
            e.clientX > r.right ||
            e.clientY < r.top ||
            e.clientY > r.bottom
          )
            onClose();
        }
      }}
    >
      <header>
        <h2 id={id}>{title}</h2>
        <Button action={onClose} aria-label="关闭">
          <X size={18} />
        </Button>
      </header>
      <div className="modal-body">{children}</div>
    </dialog>
  );
}
export function Back({ to = "/pets" }: { to?: string }) {
  return (
    <Link to={to} className="back">
      <ArrowLeft size={16} />
      返回
    </Link>
  );
}
export function Gallery({ value }: { value: any }) {
  const list = Array.isArray(value) ? value : [];
  const [active, setActive] = useState<string | null>(null);
  return (
    <>
      <div className="gallery">
        {list.map((src: string, i: number) => (
          <button
            key={i}
            className="image-button"
            onClick={() => setActive(src)}
            aria-label={`查看第 ${i + 1} 张图片`}
          >
            <img src={src} loading="lazy" alt={`内容图片 ${i + 1}`} />
          </button>
        ))}
      </div>
      {active && (
        <Modal title="图片预览" onClose={() => setActive(null)}>
          <img className="preview-image" src={active} alt="图片预览" />
        </Modal>
      )}
    </>
  );
}
export function Upload({
  value,
  onChange,
  endpoint = "/pet/upload",
  max = 9,
}: {
  value: string[];
  onChange: (v: string[]) => void;
  endpoint?: string;
  max?: number;
}) {
  const [busy, setBusy] = useState(false);
  const { notify } = useApp();
  return (
    <div>
      <div className="uploads">
        {value.map((v, i) => (
          <div className="upload-preview" key={i}>
            <img src={v} alt={`已上传图片 ${i + 1}`} />
            <button
              type="button"
              onClick={() => onChange(value.filter((_, n) => n !== i))}
              aria-label="移除图片"
            >
              <X size={15} />
            </button>
          </div>
        ))}
      </div>
      <label className="btn upload-button">
        {busy ? "上传中…" : "＋ 上传图片"}
        <input
          type="file"
          accept="image/*"
          hidden
          multiple={max > 1}
          disabled={busy || value.length >= max}
          onChange={async (e) => {
            const files = Array.from(e.target.files || []);
            const input = e.target;
            if (files.length + value.length > max) {
              notify(`最多上传 ${max} 张图片`, true);
              return;
            }
            setBusy(true);
            const uploaded = [...value];
            try {
              for (const file of files) {
                if (file.size > 2 * 1024 * 1024)
                  throw new Error("图片不能超过 2MB");
                const form = new FormData();
                form.append("file", file);
                const result = await api(endpoint, "POST", form);
                const src =
                  typeof result === "string"
                    ? result
                    : result?.url || result?.avatarUrl || result?.avatar;
                if (!src) throw new Error("未取得上传图片地址");
                uploaded.push(src);
                onChange([...uploaded]);
              }
            } catch (e: any) {
              notify(e.message, true);
            } finally {
              setBusy(false);
              input.value = "";
            }
          }}
        />
      </label>
      <small className="muted"> 图片不超过 2MB，最多 {max} 张</small>
    </div>
  );
}
