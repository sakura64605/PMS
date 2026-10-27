import { createContext, useContext, useEffect, useRef, useState } from "react";
import { AtomicRoutes, useRouteLocation } from "./routeTransition";
import { loadCharts } from "./routeModules";
import {
  BarChart3,
  Clock,
  CheckCircle2,
  AlertTriangle,
  ShieldAlert,
  KeyRound,
  Edit,
  Trash2,
  RotateCw,
  Megaphone,
  Plus,
  Users,
  PawPrint,
} from "lucide-react";
import { api, url, rows, uid, nick, time, images, typeName, auditName, type Row } from "./api";
import {
  Author,
  Avatar,
  Back,
  Badge,
  Button,
  DataState,
  Value,
  Empty,
  Field,
  Gallery,
  Link,
  Modal,
  Pagination,
  SearchForm,
  useApp,
  useData,
} from "./core";
import { AuditActions } from "./content";
const tabs = [
  ["pending", "待审核内容", Clock],
  ["history", "审核历史", CheckCircle2],
  ["users", "用户管理", Users],
  ["reports", "举报处理", AlertTriangle],
  ["stats", "数据统计", BarChart3],
  ["notices", "公告管理", Megaphone],
] as const;
const GovernanceContext = createContext<{
  pending: number | null;
  reports: number | null;
  refresh: () => void;
}>({ pending: null, reports: null, refresh: () => {} });
export default function Admin() {
  const { user } = useApp();
  const [tab, setTab] = useState("pending");
  const [displayedTab, setDisplayedTab] = useState("pending");
  const pending = useData(user?.role === 1 ? "/admin/audit/pending?pageNum=1&pageSize=1" : null);
  const reports = useData(user?.role === 1 ? "/report/list?pageNum=1&pageSize=1&status=0" : null);
  const governance = {
    pending: pending.data?.total ?? null,
    reports: reports.data?.total ?? null,
    refresh: () => {
      pending.reload();
      reports.reload();
    },
  };
  if (user?.role !== 1) return <Empty text="仅管理员可访问此页面" />;
  return (
    <section className="admin-workspace">
      <header className="governance-heading">
        <div className="governance-heading-main">
          <span className="governance-emblem">
            <ShieldAlert size={20} />
          </span>
          <div>
            <h1>管理后台 · 内容审核与社区治理</h1>
            <p>严格把关领养救助真实性，保障宠友权益与社区健康环境</p>
          </div>
        </div>
        <span className="governance-permission">管理员权限生效中</span>
      </header>
      <GovernanceContext.Provider value={governance}>
        <div className="admin-shell">
          <nav className="admin-nav" aria-label="管理模块">
            {tabs.map(([key, name, Icon]) => (
              <button
                key={key}
                className={displayedTab === key ? "active" : ""}
                aria-current={displayedTab === key ? "page" : undefined}
                onClick={() => setTab(key)}
                onPointerEnter={() => {
                  if (key === "stats") void loadCharts().catch(() => {});
                }}
                onFocus={() => {
                  if (key === "stats") void loadCharts().catch(() => {});
                }}
              >
                <Icon size={16} />
                <span>{name}</span>
                {(key === "pending" || key === "reports") && (
                  <span
                    className="governance-count"
                    aria-label={key === "pending" ? "待审核总数" : "待处理举报总数"}
                  >
                    {(key === "pending" ? governance.pending : governance.reports) ?? "—"}
                  </span>
                )}
              </button>
            ))}
          </nav>
          <div>
            <AtomicRoutes path={tab} onCommit={setDisplayedTab}>
              {(tab) => (
                <>
                  {tab === "pending" || tab === "history" ? (
                    <AuditList history={tab === "history"} />
                  ) : tab === "users" ? (
                    <UsersAdmin />
                  ) : tab === "reports" ? (
                    <Reports />
                  ) : tab === "notices" ? (
                    <NoticesAdmin />
                  ) : (
                    <Statistics />
                  )}
                </>
              )}
            </AtomicRoutes>
          </div>
        </div>
      </GovernanceContext.Provider>
    </section>
  );
}
function AuditList({ history = false }: { history?: boolean }) {
  const governance = useContext(GovernanceContext);
  const [page, setPage] = useState(1),
    [keyword, setKeyword] = useState(""),
    [type, setType] = useState(""),
    [status, setStatus] = useState(""),
    [selected, setSelected] = useState<Row[]>([]),
    [reason, setReason] = useState<string | null>(null);
  const request = useData(
    url(`/admin/audit/${history ? "history" : "pending"}`, {
      pageNum: page,
      pageSize: 12,
      keyword,
      targetType: type,
      auditStatus: history ? status : undefined,
    }),
  );
  const { notify } = useApp();
  useEffect(() => setSelected([]), [page, keyword, type, status]);
  async function batch(reject = false) {
    if (!selected.length) throw new Error("请先选择内容");
    if (new Set(selected.map((p) => p.targetType)).size > 1)
      throw new Error("批量审核请先选择同一种内容类型");
    if (reject && !reason?.trim()) throw new Error("请填写拒绝理由");
    if (!reject && !confirm(`确认通过所选 ${selected.length} 条内容？`)) return;
    await api(
      url(`/admin/audit/batch-${reject ? "reject" : "approve"}`, {
        targetType: selected[0].targetType,
      }),
      "POST",
      { ids: selected.map((p) => p.id), rejectReason: reason },
    );
    request.reload();
    governance.refresh();
    setSelected([]);
    setReason(null);
    notify("审核处理成功");
  }
  return (
    <>
      <section className="module-toolbar audit-toolbar">
        <h2 className="visually-hidden">{history ? "已审核历史记录" : "待审核内容"}</h2>
        <SearchForm
          onSearch={(s) => {
            setKeyword(s);
            setPage(1);
          }}
          placeholder="搜索标题或发布者"
        >
          <select
            aria-label="内容类型"
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setPage(1);
            }}
          >
            <option value="">全部类型</option>
            {["adopt", "help", "activity", "daily"].map((t) => (
              <option key={t} value={t}>
                {typeName(t)}
              </option>
            ))}
          </select>
          {history && (
            <select
              aria-label="审核结果"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
            >
              <option value="">全部结果</option>
              <option value="1">已通过</option>
              <option value="2">已拒绝</option>
            </select>
          )}
        </SearchForm>
        {!history && (
          <div className="row-actions" style={{ marginTop: 18 }}>
            <label className="inline-check">
              <input
                type="checkbox"
                checked={
                  !!rows(request.data).length && selected.length === rows(request.data).length
                }
                onChange={(e) => setSelected(e.target.checked ? rows(request.data) : [])}
              />
              全选本页
            </label>
            <Button tone="primary small" disabled={!selected.length} action={() => batch()}>
              批量通过（{selected.length}）
            </Button>
            <Button tone="danger small" disabled={!selected.length} action={() => setReason("")}>
              批量拒绝
            </Button>
          </div>
        )}
      </section>
      <section
        className={`governance-list ${history ? "governance-history" : "governance-pending"}`}
      >
        {history && (
          <div className="governance-list-heading">
            <h2>已审核历史记录</h2>
            <small>共 {request.data?.total ?? "—"} 条记录</small>
          </div>
        )}
        <DataState request={request}>
          {rows(request.data).map((p: Row) => {
            const id = history ? p.targetId : p.id;
            return (
              <article className="governance-row" key={`${p.targetType}-${p.id}`}>
                {!history && (
                  <input
                    type="checkbox"
                    aria-label={`选择 ${p.title}`}
                    checked={selected.some((v) => v.id === p.id && v.targetType === p.targetType)}
                    onChange={(e) =>
                      setSelected((previous) =>
                        e.target.checked
                          ? [...previous, p]
                          : previous.filter((v) => v.id !== p.id || v.targetType !== p.targetType),
                      )
                    }
                  />
                )}
                {!history && (
                  <div className="governance-thumbnail">
                    {images(p.images).length ? (
                      <Gallery value={images(p.images).slice(0, 1)} />
                    ) : (
                      <PawPrint size={20} />
                    )}
                  </div>
                )}
                <div className="governance-row-body">
                  <div className="governance-row-title">
                    <Badge tone={history ? (p.auditStatus === 2 ? "orange" : "") : "blue"}>
                      {history ? auditName(p.auditStatus) : typeName(p.targetType)}
                    </Badge>
                    <h3>{p.title || p.targetTitle}</h3>
                  </div>
                  {!history && (p.content || p.targetContent) && (
                    <p className="governance-summary">{p.content || p.targetContent}</p>
                  )}
                  <p className="governance-row-meta">
                    发布者：
                    {p.user ? nick(p.user) : p.userName || p.authorName || "—"} ·{" "}
                    {history ? `审核人：${p.auditorName || "—"} · ` : ""}
                    {time(history ? p.auditTime : p.createTime)}
                    {p.rejectReason && (
                      <span className="governance-reason">（理由：{p.rejectReason}）</span>
                    )}
                  </p>
                </div>
                <div className="governance-row-actions">
                  <Link className="btn ghost small" to={`/audit/${id}?targetType=${p.targetType}`}>
                    查看详情
                  </Link>
                  {!history && (
                    <AuditActions
                      compact
                      id={String(id)}
                      type={p.targetType}
                      reload={() => {
                        request.reload();
                        governance.refresh();
                      }}
                    />
                  )}
                </div>
              </article>
            );
          })}
          {!request.loading && !rows(request.data).length && (
            <div className="governance-empty">
              {history ? "当前筛选条件下暂无审核历史" : "🎉 暂无待审核内容，社区内容秩序良好！"}
            </div>
          )}
        </DataState>
      </section>
      <Pagination page={page} total={request.data?.total || 0} onChange={setPage} />
      {reason !== null && (
        <Modal title={`拒绝 ${selected.length} 条内容`} onClose={() => setReason(null)}>
          <Field label="拒绝理由" required>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
          <div className="tag-list">
            {["信息不完整，请补充", "内容不符合社区规范", "图片不清晰，请重新上传"].map((r) => (
              <Button tone="small" key={r} action={() => setReason(r)}>
                {r}
              </Button>
            ))}
          </div>
          <div className="form-actions">
            <Button action={() => setReason(null)}>取消</Button>
            <Button tone="danger" action={() => batch(true)}>
              确认拒绝
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
export function AuditDetail({ id }: { id: string }) {
  const type = new URLSearchParams(useRouteLocation().split("?")[1]).get("targetType") || "adopt";
  const request = useData(url("/admin/audit/detail", { targetType: type, id }));
  const p = request.data;
  return (
    <div className="form-narrow">
      <Back to="/audit" />
      <DataState request={request}>
        <article className="panel">
          <Badge tone="blue">{typeName(type)}</Badge> <Badge>{auditName(p?.auditStatus)}</Badge>
          <h1 className="detail-title">{p?.title}</h1>
          <Author user={p?.user} date={p?.createTime} />
          <Gallery value={images(p?.images)} />
          <div className="facts">
            {[
              ["地点", p?.address || p?.location],
              ["宠物", p?.petName || p?.petType],
              ["联系", p?.contactPhone],
            ].map(([l, v]) => (
              <div key={l}>
                <small>{l}</small>
                <strong>{v || "—"}</strong>
              </div>
            ))}
          </div>
          {type === "activity" && (
            <p className="muted">
              {time(p?.startTime)} — {time(p?.endTime)} · {p?.currentPeople || 0} /{" "}
              {p?.maxPeople || 0} 人
            </p>
          )}
          <p className="prose">{p?.content}</p>
          {p?.auditStatus === 0 && <AuditActions id={id} type={type} reload={request.reload} />}
        </article>
      </DataState>
    </div>
  );
}
function UsersAdmin() {
  const [page, setPage] = useState(1),
    [keyword, setKeyword] = useState(""),
    [status, setStatus] = useState(""),
    [selected, setSelected] = useState<string[]>([]);
  const request = useData(url("/admin/users", { pageNum: page, pageSize: 20, keyword, status }));
  const { notify } = useApp();
  useEffect(() => setSelected([]), [page, keyword, status]);
  async function batch(action: string, ids = selected) {
    if (!ids.length) throw new Error("请先选择用户");
    if (
      !confirm(
        `确认对 ${ids.length} 位用户执行${action === "reset-password" ? "密码重置" : action === "enable" ? "启用" : "禁用"}？`,
      )
    )
      return;
    await api(`/admin/users/batch-${action}`, "POST", { userIds: ids });
    request.reload();
    setSelected([]);
    notify("处理成功");
  }
  return (
    <section className="admin-module users-module">
      <div className="module-toolbar">
        <h2 className="visually-hidden">用户管理</h2>
        <SearchForm
          onSearch={(v) => {
            setKeyword(v);
            setPage(1);
          }}
          placeholder="用户名、昵称或手机号"
        >
          <select
            aria-label="用户状态"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(1);
            }}
          >
            <option value="">全部状态</option>
            <option value="1">正常</option>
            <option value="0">禁用</option>
          </select>
        </SearchForm>
        <div className="row-actions" style={{ margin: "18px 0" }}>
          <Button disabled={!selected.length} tone="small" action={() => batch("enable")}>
            批量启用
          </Button>
          <Button disabled={!selected.length} tone="danger small" action={() => batch("disable")}>
            批量禁用
          </Button>
          <Button
            disabled={!selected.length}
            tone="small governance-gold"
            action={() => batch("reset-password")}
          >
            <KeyRound size={14} />
            批量重置密码 ({selected.length})
          </Button>
        </div>
      </div>
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="governance-list">
          <label className="governance-select-all">
            <input
              type="checkbox"
              aria-label="全选本页用户"
              checked={
                rows(request.data).length > 0 && selected.length === rows(request.data).length
              }
              onChange={(e) => setSelected(e.target.checked ? rows(request.data).map(uid) : [])}
            />
            全选本页 · 已选 {selected.length} 位用户
          </label>
          {rows(request.data).map((u: Row) => {
            const disabled = !!u.isDisable || u.status === 0;
            return (
              <article className="governance-row" key={uid(u)}>
                <input
                  type="checkbox"
                  aria-label={`选择 ${nick(u)}`}
                  checked={selected.includes(uid(u))}
                  onChange={(e) =>
                    setSelected((previous) =>
                      e.target.checked
                        ? [...previous, uid(u)]
                        : previous.filter((v) => v !== uid(u)),
                    )
                  }
                />
                <Avatar user={u} size={40} />
                <div className="governance-row-body">
                  <div className="governance-row-title">
                    <Link to={`/user/${uid(u)}`} className="governance-user-name">
                      {nick(u)}
                    </Link>
                    <span className="governance-user-handle">@{u.username || u.userName}</span>
                    <Badge tone={disabled ? "orange" : ""}>{disabled ? "已封禁" : "正常"}</Badge>
                  </div>
                  <p className="governance-row-meta">
                    ID: {uid(u)}
                    {u.phone && ` · 手机: ${u.phone}`}
                    {u.likeCount != null && ` · 获赞 ${u.likeCount}`}
                    {u.createTime && ` · 注册于 ${time(u.createTime)}`}
                  </p>
                </div>
                <div className="governance-row-actions">
                  <Button
                    tone={disabled ? "governance-approve" : "governance-reject"}
                    action={() => batch(disabled ? "enable" : "disable", [uid(u)])}
                  >
                    {disabled ? "解封恢复" : "封禁账号"}
                  </Button>
                </div>
              </article>
            );
          })}
        </div>
      </DataState>
      <Pagination page={page} size={20} total={request.data?.total || 0} onChange={setPage} />
    </section>
  );
}
function Reports() {
  const governance = useContext(GovernanceContext);
  const [decision, setDecision] = useState("1");
  const [page, setPage] = useState(1),
    [status, setStatus] = useState(""),
    [type, setType] = useState(""),
    [active, setActive] = useState<string | null>(null);
  const request = useData(
    url("/report/list", {
      pageNum: page,
      pageSize: 20,
      status,
      targetType: type,
    }),
  );
  return (
    <section className="admin-module reports-module">
      <div className="module-toolbar reports-toolbar">
        <div className="governance-info-bar">
          <span>核实举报事实，维护良好社区交流秩序</span>
          <strong>待处理举报：{governance.reports ?? "—"} 件</strong>
        </div>
        <div className="toolbar">
          <select
            aria-label="处理状态"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(1);
            }}
          >
            <option value="">全部状态</option>
            {["待处理", "已处理", "已驳回"].map((v, i) => (
              <option value={i} key={i}>
                {v}
              </option>
            ))}
          </select>
          <select
            aria-label="举报目标类型"
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setPage(1);
            }}
          >
            <option value="">全部类型</option>
            {["pet", "activity", "comment", "user"].map((v) => (
              <option value={v} key={v}>
                {typeName(v)}
              </option>
            ))}
          </select>
        </div>
      </div>
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="governance-list">
          {rows(request.data).map((p: Row) => (
            <article className="governance-row" key={p.id}>
              <div className="governance-row-body">
                <div className="governance-row-title">
                  <Badge tone={p.status === 0 ? "orange" : ""}>
                    {["待处理", "已处理完成", "已驳回"][p.status]}
                  </Badge>
                  <h3>{p.targetTitle || `${typeName(p.targetType)} #${p.targetId}`}</h3>
                </div>
                <p className="governance-row-meta">
                  举报原因：
                  <strong className="governance-reason">{p.reason}</strong> · 举报人：
                  {p.reporterName || "—"} · {time(p.createTime)}
                </p>
                {p.handleResult && <p className="governance-summary">处理说明：{p.handleResult}</p>}
              </div>
              <div className="governance-row-actions">
                {p.status === 0 ? (
                  <>
                    <Button
                      tone="governance-terra"
                      action={() => {
                        setDecision("1");
                        setActive(String(p.id));
                      }}
                    >
                      下架违规内容
                    </Button>
                    <Button
                      action={() => {
                        setDecision("2");
                        setActive(String(p.id));
                      }}
                    >
                      驳回举报
                    </Button>
                  </>
                ) : (
                  <Button tone="ghost" action={() => setActive(String(p.id))}>
                    处理详情
                  </Button>
                )}
              </div>
            </article>
          ))}
        </div>
      </DataState>
      <Pagination page={page} size={20} total={request.data?.total || 0} onChange={setPage} />
      {active && (
        <ReportDetail
          id={active}
          onClose={() => setActive(null)}
          initialStatus={decision}
          reload={() => {
            request.reload();
            governance.refresh();
          }}
        />
      )}
    </section>
  );
}
function ReportDetail({
  id,
  onClose,
  reload,
  initialStatus = "1",
}: {
  id: string;
  onClose: () => void;
  reload: () => void;
  initialStatus?: string;
}) {
  const request = useData(`/report/detail/${id}`);
  const [status, setStatus] = useState(initialStatus),
    [text, setText] = useState("");
  const p = request.data;
  return (
    <Modal title="举报详情与处理" onClose={onClose}>
      <DataState request={request}>
        <p>
          <Badge>{typeName(p?.targetType)}</Badge>　目标 #{p?.targetId}
        </p>
        <h3>{p?.targetTitle}</h3>
        <p className="prose">{p?.reason}</p>
        <p className="muted">
          {p?.reporterName} · {time(p?.createTime)}
        </p>
        {p?.targetType !== "comment" && (
          <Link
            className="btn small"
            to={
              p?.targetType === "user"
                ? `/user/${p?.targetId}`
                : p?.targetType === "activity"
                  ? `/pets/activity/${p?.targetId}`
                  : `/pets/${p?.targetId}`
            }
          >
            查看内容
          </Link>
        )}
        {p?.status === 0 ? (
          <>
            <Field label="处理结果">
              <select value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="1">已处理（下架）</option>
                <option value="2">已驳回</option>
              </select>
            </Field>
            <Field label="处理说明" required>
              <textarea value={text} onChange={(e) => setText(e.target.value)} />
            </Field>
            <div className="form-actions">
              <Button action={onClose}>取消</Button>
              <Button
                tone="primary"
                action={async () => {
                  if (!text.trim()) throw new Error("请填写处理说明");
                  await api(url(`/report/handle/${id}`, { status, handleResult: text }), "POST");
                  reload();
                  onClose();
                }}
              >
                确认处理
              </Button>
            </div>
          </>
        ) : (
          <p className="muted">
            {p?.handleResult} · {p?.handlerName} · {time(p?.handleTime)}
          </p>
        )}
      </DataState>
    </Modal>
  );
}
function NoticesAdmin() {
  const [page, setPage] = useState(1),
    [keyword, setKeyword] = useState(""),
    [status, setStatus] = useState(""),
    [editing, setEditing] = useState<Row | null>(null);
  const request = useData(
    url("/notice/admin/list", { pageNum: page, pageSize: 20, keyword, status }),
  );
  async function act(path: string, method: string) {
    await api(path, method);
    request.reload();
  }
  return (
    <section className="admin-module notices-module">
      <div className="module-toolbar">
        <div className="panel-title">
          <span className="governance-info-text">管理系统公告、活动通知与重要提醒</span>
          <Button
            tone="primary"
            action={() =>
              setEditing({
                title: "",
                content: "",
                type: 1,
                priority: 0,
                isTop: 0,
              })
            }
          >
            <Plus size={14} />
            新建系统公告
          </Button>
        </div>
        <SearchForm
          onSearch={(v) => {
            setKeyword(v);
            setPage(1);
          }}
          placeholder="搜索公告标题或内容"
        >
          <select
            aria-label="公告状态"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(1);
            }}
          >
            <option value="">全部状态</option>
            {["草稿", "已发布", "已下线"].map((s, i) => (
              <option value={i} key={i}>
                {s}
              </option>
            ))}
          </select>
        </SearchForm>
      </div>
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="governance-list">
          {rows(request.data).map((p: Row) => (
            <article className="governance-row" key={p.id}>
              <div className="governance-row-body">
                <div className="governance-row-title">
                  {p.isTop === 1 && <Badge tone="orange">置顶</Badge>}
                  <h3>
                    {p.status === 1 ? <Link to={`/notice/${p.id}`}>{p.title}</Link> : p.title}
                  </h3>
                  <Badge>{["草稿", "已发布", "已下线"][p.status]}</Badge>
                </div>
                {p.content && <p className="governance-summary">{p.content}</p>}
                <p className="governance-row-meta">
                  {["", "系统公告", "活动通知", "重要提醒"][p.type]} · 发布于 {time(p.publishTime)}{" "}
                  · 优先级：
                  {["普通", "重要", "紧急"][p.priority]}
                </p>
              </div>
              <div className="governance-row-actions">
                <Button
                  aria-label={`编辑公告：${p.title}`}
                  title="编辑公告"
                  action={async () => {
                    const detail = await api(`/notice/admin/${p.id}`);
                    setEditing({ ...p, ...detail });
                  }}
                >
                  <Edit size={14} />
                </Button>
                <Button
                  tone="small"
                  action={() =>
                    act(`/notice/admin/${p.id}/${p.status === 1 ? "unpublish" : "publish"}`, "PUT")
                  }
                >
                  {p.status === 1 ? "下架" : "发布"}
                </Button>
                <Button
                  tone="governance-icon-danger"
                  aria-label={`删除公告：${p.title}`}
                  title="删除公告"
                  action={async () => {
                    if (confirm("确认删除公告？")) await act(`/notice/admin/${p.id}`, "DELETE");
                  }}
                >
                  <Trash2 size={14} />
                </Button>
              </div>
            </article>
          ))}
        </div>
      </DataState>
      <Pagination page={page} size={20} total={request.data?.total || 0} onChange={setPage} />
      {editing && (
        <NoticeForm value={editing} onClose={() => setEditing(null)} reload={request.reload} />
      )}
    </section>
  );
}
function NoticeForm({
  value,
  onClose,
  reload,
}: {
  value: Row;
  onClose: () => void;
  reload: () => void;
}) {
  const [form, set] = useState(value);
  const f = (k: string, v: any) => set((p) => ({ ...p, [k]: v }));
  return (
    <Modal title={form.id ? "编辑公告" : "新增公告"} onClose={onClose}>
      <Field label="标题" required>
        <input value={form.title} maxLength={100} onChange={(e) => f("title", e.target.value)} />
      </Field>
      <Field label="正文" required>
        <textarea
          rows={7}
          value={form.content || ""}
          onChange={(e) => f("content", e.target.value)}
        />
      </Field>
      <div className="form-grid">
        <Field label="类型">
          <select value={form.type} onChange={(e) => f("type", Number(e.target.value))}>
            {["系统公告", "活动通知", "重要提醒"].map((v, i) => (
              <option key={i} value={i + 1}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        <Field label="优先级">
          <select value={form.priority} onChange={(e) => f("priority", Number(e.target.value))}>
            {["普通", "重要", "紧急"].map((v, i) => (
              <option key={i} value={i}>
                {v}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <label className="inline-check">
        <input
          type="checkbox"
          checked={form.isTop === 1}
          onChange={(e) => f("isTop", e.target.checked ? 1 : 0)}
        />
        置顶
      </label>
      {!form.id && (
        <Field label="定时发布时间（可选）">
          <input
            type="datetime-local"
            value={form.schedulePublishTime || ""}
            onChange={(e) => f("schedulePublishTime", e.target.value)}
          />
        </Field>
      )}
      <div className="form-actions">
        <Button action={onClose}>取消</Button>
        <Button
          tone="primary"
          action={async () => {
            if (!form.title.trim() || !form.content?.trim()) throw new Error("请填写标题和内容");
            const body = {
              id: form.id,
              title: form.title,
              content: form.content,
              type: form.type,
              priority: form.priority,
              isTop: form.isTop,
              schedulePublishTime: form.schedulePublishTime
                ? form.schedulePublishTime + ":00"
                : undefined,
            };
            await api(
              form.id ? "/notice/admin/update" : "/notice/admin/create",
              form.id ? "PUT" : "POST",
              body,
            );
            reload();
            onClose();
          }}
        >
          保存
        </Button>
      </div>
    </Modal>
  );
}
function Trend({ data }: { data: Row }) {
  const [metric, setMetric] = useState("newPostList");
  const metrics = [
    ["newPostList", "新增帖子"],
    ["newUserList", "新增用户"],
    ["newCommentList", "新增评论"],
    ["newLikeList", "新增点赞"],
    ["dauList", "活跃用户"],
  ];
  const ref = useRef<HTMLDivElement>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let disposed = false;
    let chart: any;
    let observer: ResizeObserver;
    loadCharts()
      .then((e) => {
        if (disposed || !ref.current) return;
        chart = e.init(ref.current);
        chart.setOption({
          color: ["#5f7161", "#c37e63", "#9aa994", "#637888", "#c5ad89"],
          tooltip: { trigger: "axis" },
          legend: { bottom: 0, textStyle: { color: "#8a827b" } },
          grid: { top: 20, left: 40, right: 20, bottom: 75 },
          xAxis: {
            type: "category",
            data: data.dates,
            axisLabel: { color: "#8a827b" },
          },
          yAxis: {
            type: "value",
            splitLine: { lineStyle: { color: "#f2ede5" } },
          },
          series: [
            {
              name: metrics.find(([key]) => key === metric)?.[1],
              type: "bar",
              label: {
                show: true,
                position: "top",
                color: "#8a827b",
                fontSize: 10,
              },
              barMaxWidth: 36,
              itemStyle: { color: "#859887", borderRadius: [7, 7, 0, 0] },
              emphasis: { itemStyle: { color: "#5f7161" } },
              data: data[metric] || [],
            },
          ],
        });
        observer = new ResizeObserver(() => chart?.resize());
        observer.observe(ref.current);
      })
      .catch((e) => setError(e.message));
    return () => {
      disposed = true;
      observer?.disconnect();
      chart?.dispose();
    };
  }, [data, metric]);
  return error ? (
    <p role="alert">图表加载失败：{error}</p>
  ) : (
    <div className="governance-chart">
      <div className="governance-chart-heading">
        <span>新增趋势</span>
        <select aria-label="趋势指标" value={metric} onChange={(e) => setMetric(e.target.value)}>
          {metrics.map(([key, name]) => (
            <option key={key} value={key}>
              {name}
            </option>
          ))}
        </select>
      </div>
      <div
        ref={ref}
        role="img"
        aria-label="社区数据趋势柱状图"
        style={{ height: 220, width: "100%" }}
      />
    </div>
  );
}
function Statistics() {
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  const [type, setType] = useState("weekly"),
    [date, setDate] = useState(today),
    [end, setEnd] = useState(today),
    [query, setQuery] = useState<Row>({ type: "weekly", date: today }),
    [backfill, setBackfill] = useState(false),
    [start, setStart] = useState(today),
    [finish, setFinish] = useState(today),
    [range, setRange] = useState(false),
    [progress, setProgress] = useState("");
  const request = useData(url("/admin/statistics/query", query)),
    realtime = useData("/admin/statistics/realtime");
  const { notify } = useApp();
  const d = request.data || {},
    r = realtime.data || {};
  const counters = [
    ["新增用户", d.newUserCount ?? d.totalNewUsers],
    [
      "活跃用户",
      d.activeUserCount ?? d.dau ?? d.totalActiveUsers ?? d.avgDailyActiveUsers ?? d.avgActiveUsers,
    ],
    [
      "新增帖子",
      d.totalNewPosts ??
        (d.newPetPostCount || 0) + (d.newActivityCount || 0) + (d.newDailyPostCount || 0),
    ],
    ["新增评论", d.newCommentCount ?? d.totalNewComments],
  ];
  return (
    <>
      <section className="governance-realtime">
        <div className="governance-stat-overline">
          <span>今日实时</span>
          <Button tone="small" action={realtime.reload}>
            刷新
          </Button>
        </div>
        <DataState request={realtime}>
          <div className="governance-stat-cards">
            {[
              ["今日新增用户", r.newUserCount],
              [
                "今日发布帖子",
                (r.newPetPostCount || 0) + (r.newActivityCount || 0) + (r.newDailyPostCount || 0),
              ],
              ["今日互动评论", r.newCommentCount],
              ["今日收获点赞", r.newLikeCount],
              ["待审核内容", r.pendingAuditCount],
            ].map(([name, n]) => (
              <div className="stat-tile" key={name}>
                <span>{name}</span>
                <strong>
                  <Value>{n ?? 0}</Value>
                </strong>
                <small>
                  {name === "待审核内容"
                    ? "严格把关内容真实性"
                    : name === "今日收获点赞"
                      ? "社区爱心传递"
                      : "今日实时更新"}
                </small>
              </div>
            ))}
          </div>
        </DataState>
      </section>
      <section className="panel">
        <h2 className="visually-hidden">统计概览</h2>
        <div className="toolbar">
          <div className="governance-periods" aria-label="统计周期">
            <strong>统计周期：</strong>
            {[
              ["daily", "日"],
              ["weekly", "周"],
              ["monthly", "月"],
              ["yearly", "年"],
              ["range", "区间"],
            ].map(([value, name]) => (
              <button
                key={value}
                type="button"
                aria-pressed={type === value}
                className={type === value ? "active" : ""}
                onClick={() => {
                  setType(value);
                  const selected =
                    value === "monthly"
                      ? today.slice(0, 7)
                      : value === "yearly"
                        ? today.slice(0, 4)
                        : today;
                  setDate(selected);
                  setQuery(
                    value === "monthly"
                      ? { type: value, month: selected }
                      : value === "yearly"
                        ? { type: value, year: selected }
                        : value === "range"
                          ? { type: value, startDate: today, endDate: today }
                          : { type: value, date: selected },
                  );
                }}
              >
                {name}
              </button>
            ))}
          </div>
          <input
            aria-label="统计日期"
            type={type === "monthly" ? "month" : type === "yearly" ? "number" : "date"}
            value={date}
            style={{ width: 160 }}
            onChange={(e) => setDate(e.target.value)}
          />
          {type === "range" && (
            <input
              type="date"
              aria-label="截止日期"
              value={end}
              style={{ width: 160 }}
              onChange={(e) => setEnd(e.target.value)}
            />
          )}
          <Button
            tone="primary"
            action={() => {
              if (!date || (type === "range" && (!end || end < date)))
                throw new Error("请检查日期范围");
              setQuery(
                type === "monthly"
                  ? { type, month: date }
                  : type === "yearly"
                    ? { type, year: date }
                    : type === "range"
                      ? { type, startDate: date, endDate: end }
                      : { type, date },
              );
            }}
          >
            查询
          </Button>
          <Button action={() => setBackfill(true)}>
            <RotateCw size={14} />
            数据重算 / 单日补录
          </Button>
        </div>
        <DataState request={request}>
          <div className="governance-period-summary">
            {counters.map(([name, n]) => (
              <div className="stat-tile" key={name}>
                <strong>
                  <Value>{n ?? 0}</Value>
                </strong>
                <span>{name}</span>
              </div>
            ))}
          </div>
          {d.trendData?.dates?.length ? (
            <Trend data={d.trendData} />
          ) : (
            <Empty text="当前周期暂无趋势数据" />
          )}
        </DataState>
      </section>
      {backfill && (
        <Modal
          title="补录统计数据"
          onClose={() => {
            if (!progress) setBackfill(false);
          }}
        >
          <p className="muted">重新计算所选日期，可能覆盖原有统计信息。</p>
          <label className="inline-check">
            <input type="checkbox" checked={range} onChange={(e) => setRange(e.target.checked)} />
            区间补录
          </label>
          <Field label={range ? "开始日期" : "补录日期"}>
            <input type="date" value={start} onChange={(e) => setStart(e.target.value)} />
          </Field>
          {range && (
            <Field label="结束日期">
              <input type="date" value={finish} onChange={(e) => setFinish(e.target.value)} />
            </Field>
          )}
          {progress && <p role="status">{progress}</p>}
          <div className="form-actions">
            <Button disabled={!!progress} action={() => setBackfill(false)}>
              取消
            </Button>
            <Button
              tone="primary"
              action={async () => {
                if (!start || (range && (!finish || finish < start)))
                  throw new Error("请检查补录日期");
                setProgress("正在补录，请稍候…");
                try {
                  await api(
                    range
                      ? url("/admin/statistics/regenerate/range", {
                          startDate: start,
                          endDate: finish,
                        })
                      : url("/admin/statistics/regenerate", { date: start }),
                    "POST",
                  );
                  request.reload();
                  realtime.reload();
                  notify("补录完成");
                  setBackfill(false);
                } finally {
                  setProgress("");
                }
              }}
            >
              确认补录
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
