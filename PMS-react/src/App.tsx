import { queryCache } from "./queryCache.ts";
import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  Bell,
  Check,
  ChevronDown,
  Heart,
  MessageCircle,
  PawPrint,
  Plus,
  ShieldCheck,
  X,
} from "lucide-react";
import { api, nick, uid, type Row } from "./api";
import {
  AppContext,
  Avatar,
  Badge,
  Busy,
  Button,
  Empty,
  Intro,
  Link,
  navigate,
  useData,
  usePath,
  useApp,
} from "./core";
import { Detail, Editor, Home, MyContent, Notice, Pets } from "./content";
import {
  Auth,
  Diaries,
  Feed,
  Messages,
  Profile,
  PublishDiary,
  UserPage,
} from "./social";
const Admin = lazy(() => import("./admin"));
const AuditDetail = lazy(() =>
  import("./admin").then((m) => ({ default: m.AuditDetail })),
);
const AIChat = lazy(() =>
  import("./chat").then((m) => ({ default: m.AIChat })),
);
const PrivateChat = lazy(() =>
  import("./chat").then((m) => ({ default: m.PrivateChat })),
);

function Header({ path }: { path: string }) {
  const { user, refresh } = useApp();
  const counts = useData(user ? "/message/unread-count" : null),
    privateCounts = useData(user ? "/message/private/unread-count" : null);
  const menu = useRef<HTMLDetailsElement>(null),
    publish = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    if (menu.current) menu.current.open = false;
    if (publish.current) publish.current.open = false;
  }, [path]);
  useEffect(() => {
    if (!user) return;
    const controller = new AbortController();
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") {
        counts.reload();
        privateCounts.reload();
      }
    }, 30000);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [uid(user)]);
  async function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("userInfo");
    window.dispatchEvent(new Event("auth-cleared"));
    navigate("/login");
  }
  const active = (p: string) =>
    path.split("?")[0] === p || (p === "/pets" && path.startsWith("/pets/"));
  return (
    <header className="site-header">
      <div className="header-inner">
        <Link to="/dashboard" className="brand">
          <span className="brand-icon">
            <PawPrint size={23} />
          </span>
          <div>
            <strong>PetCircle · 宠友社</strong>
            <small>温暖清爽的宠友互助社区</small>
          </div>
        </Link>
        <nav className="nav" aria-label="主导航">
          {[
            ["/dashboard", "首页"],
            ["/pets", "宠友圈"],
            ["/daily", "宠友日记"],
            ["/feed", "关注动态"],
            ["/ai-chat", "AI养宠顾问"],
          ].map(([p, n]) => (
            <Link
              key={p}
              to={p}
              className={active(p) ? "active" : ""}
              aria-current={active(p) ? "page" : undefined}
            >
              {n}
            </Link>
          ))}
          {user?.role === 1 && (
            <Link
              to="/audit"
              className={`admin-link ${path.startsWith("/audit") ? "active" : ""}`}
            >
              <ShieldCheck size={13} style={{ verticalAlign: "middle" }} />{" "}
              管理后台
            </Link>
          )}
        </nav>
        <div className="header-actions">
          {user ? (
            <>
              <details className="menu" ref={publish}>
                <summary className="btn primary" aria-label="发布内容">
                  <Plus size={15} />
                  <span className="publish-text">发布</span>
                </summary>
                <div className="dropdown">
                  <Link to="/pets/create">领养 / 救助</Link>
                  <Link to="/pets/create?type=2">同城活动</Link>
                  <Link to="/daily/publish">宠友日记</Link>
                </div>
              </details>
              <Link
                to="/private-message"
                className="icon-btn"
                aria-label={`私信，${privateCounts.data || 0}条未读`}
                title="私信"
              >
                <MessageCircle size={20} />
                {privateCounts.data > 0 && (
                  <span className="counter">
                    {Math.min(99, privateCounts.data)}
                  </span>
                )}
              </Link>
              <Link
                to="/message"
                className="icon-btn"
                aria-label={`通知，${counts.data || 0}条未读`}
                title="通知"
              >
                <Bell size={20} />
                {counts.data > 0 && (
                  <span className="counter">
                    {counts.data > 99 ? "99+" : counts.data}
                  </span>
                )}
              </Link>
              <details className="menu" ref={menu}>
                <summary aria-label="我的账号">
                  <Avatar user={user} size={33} />
                  <span className="user-name" style={{ fontSize: 11 }}>
                    {nick(user).slice(0, 8)}
                  </span>
                  <ChevronDown size={12} />
                </summary>
                <div className="dropdown">
                  <small>
                    {nick(user)} · @{user.userName || user.username}
                  </small>
                  {[
                    ["/profile", "个人中心"],
                    ["/pets/my-posts", "我的发布"],
                    ["/pets/collections", "我的收藏"],
                    ["/recycle", "回收站"],
                  ].map(([p, n]) => (
                    <Link key={p} to={p}>
                      {n}
                    </Link>
                  ))}
                  <Button action={logout}>退出登录</Button>
                </div>
              </details>
            </>
          ) : (
            <Link to="/login" className="btn primary">
              登录 / 注册
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
const publicRoute = (path: string) =>
  ["/", "/pets", "/dashboard", "/daily", "/login", "/register"].includes(
    path,
  ) ||
  /^\/pets\/(?:activity\/)?\d+$/.test(path) ||
  /^\/daily\/\d+$/.test(path) ||
  /^\/notice\/\d+$/.test(path);
function Router({ path }: { path: string }) {
  const { user } = useApp();
  const p = path.split("?")[0];
  if (!publicRoute(p) && !user)
    return (
      <>
        <Intro title="登录后，和宠友一起分享" />
        <Empty text="这个页面需要先登录">
          <Link
            className="btn primary"
            to={`/login?next=${encodeURIComponent(path)}`}
          >
            去登录
          </Link>
        </Empty>
      </>
    );
  if (p === "/" || p === "/pets") return <Pets key={path} />;
  if (p === "/dashboard") return <Home />;
  if (p === "/pets/create")
    return (
      <Editor
        id={new URLSearchParams(location.search).get("id") || undefined}
        activity={new URLSearchParams(location.search).get("type") === "2"}
      />
    );
  if (p === "/pets/my-posts") return <MyContent />;
  if (p === "/pets/collections") return <MyContent mode="collections" />;
  if (p === "/recycle") return <MyContent mode="recycle" />;
  let match = p.match(/^\/pets\/(\d+)\/edit$/);
  if (match) return <Editor id={match[1]} />;
  match = p.match(/^\/pets\/activity\/(\d+)$/);
  if (match) return <Detail id={match[1]} activity />;
  match = p.match(/^\/pets\/(\d+)$/);
  if (match) return <Detail id={match[1]} />;
  if (p === "/daily/publish") return <PublishDiary />;
  match = p.match(/^\/daily\/(\d+)$/);
  if (match) return <Diaries id={match[1]} />;
  if (p === "/daily") return <Diaries />;
  if (p === "/feed") return <Feed />;
  if (p === "/profile") return <Profile />;
  match = p.match(/^\/user\/(\d+)$/);
  if (match) return <UserPage id={match[1]} />;
  if (p === "/message") return <Messages />;
  if (p === "/private-message") return <PrivateChat />;
  if (p === "/ai-chat") return <AIChat />;
  if (p === "/audit" || /^\/audit\/\d+$/.test(p)) {
    if (user?.role !== 1) return <Empty text="仅管理员可访问此页面" />;
    const id = p.match(/^\/audit\/(\d+)$/)?.[1];
    return id ? <AuditDetail id={id} /> : <Admin />;
  }
  match = p.match(/^\/notice\/(\d+)$/);
  if (match) return <Notice id={match[1]} />;
  if (p === "/settings")
    return (
      <>
        <Intro title="系统设置" />
        <Empty
          text={
            user?.role === 1 ? "当前暂无可配置项目" : "仅管理员可访问此页面"
          }
        />
      </>
    );
  return (
    <Empty text="这个页面暂时找不到了">
      <Link className="btn primary" to="/pets">
        返回宠友圈
      </Link>
    </Empty>
  );
}
export default function App() {
  const path = usePath();
  const [user, setUser] = useState<Row | null>(null),
    [boot, setBoot] = useState(!!localStorage.getItem("token")),
    [toast, setToast] = useState<{ text: string; error: boolean } | null>(null),
    [revision, setRevision] = useState(0);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );
  const notify = useCallback((text: string, error = false) => {
    setToast({ text, error });
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 6000);
  }, []);
  const refresh = useCallback(() => setRevision((v) => v + 1), []);
  const refreshUser = useCallback(async () => {
    if (!localStorage.getItem("token")) {
      setUser(null);
      return;
    }
    const u = await api("/user/info");
    setUser(u);
    localStorage.setItem("userInfo", JSON.stringify(u));
  }, []);
  useEffect(() => {
    if (localStorage.getItem("token"))
      refreshUser()
        .catch((e) => notify(e.message, true))
        .finally(() => setBoot(false));
    const clear = () => {
      queryCache.clear();
      setUser(null);
      setBoot(false);
    };
    window.addEventListener("auth-cleared", clear);
    return () => {
      window.removeEventListener("auth-cleared", clear);
      clearTimeout(toastTimer.current);
    };
  }, []);
  useEffect(() => {
    if (!user) return;
    let disposed = false,
      socket: any;
    let timer: ReturnType<typeof setTimeout>;
    let attempt = 0;
    const token = localStorage.getItem("token");
    async function connect() {
      try {
        const { default: SockJS } = await import("sockjs-client");
        if (disposed) return;
        socket = new SockJS(
          `/pet-system/ws?token=${encodeURIComponent(token || "")}`,
        );
        socket.onmessage = () => {
          refresh();
          window.dispatchEvent(new Event("server-message"));
        };
        socket.onopen = () => {
          attempt = 0;
        };
        socket.onclose = () => {
          if (!disposed && attempt++ < 5)
            timer = setTimeout(connect, Math.min(30000, 2000 * 2 ** attempt));
        };
      } catch {
        /* unread counts continue with bounded polling */
      }
    }
    void connect();
    return () => {
      disposed = true;
      clearTimeout(timer);
      socket?.close();
    };
  }, [uid(user)]);
  useEffect(() => {
    document.title = `${path.startsWith("/audit") ? "管理工作台" : path.startsWith("/ai-chat") ? "AI养宠顾问" : path.startsWith("/private-message") ? "私信" : "宠友社区"} · PetCircle 宠友社`;
  }, [path]);
  const auth =
    path.split("?")[0] === "/login" || path.split("?")[0] === "/register";
  return (
    <AppContext.Provider
      value={{ user, refreshUser, notify, revision, refresh }}
    >
      {auth ? (
        <Auth register={path.startsWith("/register")} />
      ) : (
        <div className="shell">
          <Header path={path} />
          <main className="page">
            <Suspense fallback={<Busy />}>
              {boot ? <Busy /> : <Router key={path} path={path} />}
            </Suspense>
          </main>
          <footer className="site-footer">
            <span>
              <strong>PetCircle · 宠友社</strong>　用心陪伴，以爱相聚
            </span>
            <span>
              领养 · 互助 · 分享　© {new Date().getFullYear()} PetCircle
            </span>
          </footer>
        </div>
      )}
      {toast && (
        <div
          className={`toast ${toast.error ? "error" : ""}`}
          role={toast.error ? "alert" : "status"}
        >
          {toast.error ? <X size={16} /> : <Check size={16} />}
          <span>{toast.text}</span>
          <button onClick={() => setToast(null)} aria-label="关闭提示">
            <X size={14} />
          </button>
        </div>
      )}
    </AppContext.Provider>
  );
}
