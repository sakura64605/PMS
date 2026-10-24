import { useEffect, useRef, useState } from "react";
import {
  BarChart3,
  CheckCheck,
  ClipboardCheck,
  Flag,
  History,
  Megaphone,
  Plus,
  ShieldCheck,
  Users,
} from "lucide-react";
import {
  api,
  url,
  rows,
  uid,
  nick,
  time,
  images,
  typeName,
  auditName,
  type Row,
} from "./api";
import {
  Author,
  Back,
  Badge,
  Button,
  DataState,
  Value,
  Empty,
  Field,
  Gallery,
  Intro,
  Link,
  Modal,
  Pagination,
  SearchForm,
  useApp,
  useData,
} from "./core";
import { AuditActions } from "./content";
const tabs = [
  ["pending", "待审核", ClipboardCheck],
  ["history", "审核历史", History],
  ["users", "用户管理", Users],
  ["reports", "举报管理", Flag],
  ["stats", "数据统计", BarChart3],
  ["notices", "公告管理", Megaphone],
] as const;
export default function Admin() {
  const { user } = useApp();
  const [tab, setTab] = useState("pending");
  if (user?.role !== 1) return <Empty text="仅管理员可访问此页面" />;
  return (
    <>
      <Intro
        eyebrow="COMMUNITY WORKSPACE"
        title="社区管理工作台"
        description="认真对待每一份分享，守护温暖而可信的社区。"
      >
        <Badge tone="blue">
          <ShieldCheck size={13} />
          管理员
        </Badge>
      </Intro>
      <div className="admin-shell">
        <nav className="admin-nav" aria-label="管理模块">
          {tabs.map(([key, name, Icon]) => (
            <button
              key={key}
              className={tab === key ? "active" : ""}
              onClick={() => setTab(key)}
            >
              <Icon size={17} />
              {name}
            </button>
          ))}
        </nav>
        <div key={tab}>
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
        </div>
      </div>
    </>
  );
}
function AuditList({ history = false }: { history?: boolean }) {
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
    setSelected([]);
    setReason(null);
    notify("审核处理成功");
  }
  return (
    <>
      <section className="panel">
        <h2>{history ? "审核历史" : "待审核内容"}</h2>
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
                  !!rows(request.data).length &&
                  selected.length === rows(request.data).length
                }
                onChange={(e) =>
                  setSelected(e.target.checked ? rows(request.data) : [])
                }
              />
              全选本页
            </label>
            <Button
              tone="primary small"
              disabled={!selected.length}
              action={() => batch()}
            >
              批量通过（{selected.length}）
            </Button>
            <Button
              tone="danger small"
              disabled={!selected.length}
              action={() => setReason("")}
            >
              批量拒绝
            </Button>
          </div>
        )}
      </section>
      <DataState request={request} empty={!rows(request.data).length}>
        {rows(request.data).map((p: Row) => {
          const content = p.content || p.targetContent || "";
          const id = history ? p.targetId : p.id;
          return (
            <article className="panel" key={`${p.targetType}-${p.id}`}>
              <div
                className="row-actions"
                style={{ justifyContent: "space-between" }}
              >
                <div className="row-actions">
                  {!history && (
                    <input
                      aria-label={`选择 ${p.title}`}
                      type="checkbox"
                      checked={selected.some(
                        (v) => v.id === p.id && v.targetType === p.targetType,
                      )}
                      onChange={(e) =>
                        setSelected((s) =>
                          e.target.checked
                            ? [...s, p]
                            : s.filter(
                                (v) =>
                                  v.id !== p.id ||
                                  v.targetType !== p.targetType,
                              ),
                        )
                      }
                    />
                  )}
                  <Badge tone="blue">{typeName(p.targetType)}</Badge>
                  <Badge tone={p.auditStatus === 2 ? "orange" : ""}>
                    {auditName(p.auditStatus)}
                  </Badge>
                </div>
                <small>{time(history ? p.auditTime : p.createTime)}</small>
              </div>
              <h3 style={{ marginTop: 14 }}>{p.title || p.targetTitle}</h3>
              {p.user && <Author user={p.user} date={p.createTime} />}
              <p className="card-description">{content}</p>
              {p.rejectReason && (
                <p className="inline-error">拒绝理由：{p.rejectReason}</p>
              )}
              {history && (
                <p className="muted">审核人：{p.auditorName || "—"}</p>
              )}
              {images(p.images).length > 0 && (
                <Gallery value={images(p.images).slice(0, 3)} />
              )}
              <div className="row-actions">
                <Link
                  className="btn small"
                  to={`/audit/${id}?targetType=${p.targetType}`}
                >
                  查看详情
                </Link>
                {!history && (
                  <AuditActions
                    id={String(id)}
                    type={p.targetType}
                    reload={request.reload}
                  />
                )}
              </div>
            </article>
          );
        })}
      </DataState>
      <Pagination
        page={page}
        total={request.data?.total || 0}
        onChange={setPage}
      />
      {reason !== null && (
        <Modal
          title={`拒绝 ${selected.length} 条内容`}
          onClose={() => setReason(null)}
        >
          <Field label="拒绝理由" required>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </Field>
          <div className="tag-list">
            {[
              "信息不完整，请补充",
              "内容不符合社区规范",
              "图片不清晰，请重新上传",
            ].map((r) => (
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
  const type =
    new URLSearchParams(location.search).get("targetType") || "adopt";
  const request = useData(url("/admin/audit/detail", { targetType: type, id }));
  const p = request.data;
  return (
    <div className="form-narrow">
      <Back to="/audit" />
      <DataState request={request}>
        <article className="panel">
          <Badge tone="blue">{typeName(type)}</Badge>{" "}
          <Badge>{auditName(p?.auditStatus)}</Badge>
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
              {time(p?.startTime)} — {time(p?.endTime)} ·{" "}
              {p?.currentPeople || 0} / {p?.maxPeople || 0} 人
            </p>
          )}
          <p className="prose">{p?.content}</p>
          {p?.auditStatus === 0 && (
            <AuditActions id={id} type={type} reload={request.reload} />
          )}
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
  const request = useData(
    url("/admin/users", { pageNum: page, pageSize: 20, keyword, status }),
  );
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
    <section className="panel">
      <h2>用户管理</h2>
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
        <Button
          disabled={!selected.length}
          tone="small"
          action={() => batch("enable")}
        >
          批量启用
        </Button>
        <Button
          disabled={!selected.length}
          tone="danger small"
          action={() => batch("disable")}
        >
          批量禁用
        </Button>
        <Button
          disabled={!selected.length}
          tone="small"
          action={() => batch("reset-password")}
        >
          批量重置密码
        </Button>
      </div>
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="全选本页用户"
                    checked={
                      rows(request.data).length > 0 &&
                      selected.length === rows(request.data).length
                    }
                    onChange={(e) =>
                      setSelected(
                        e.target.checked ? rows(request.data).map(uid) : [],
                      )
                    }
                  />
                </th>
                <th>ID</th>
                <th>用户</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {rows(request.data).map((u) => (
                <tr key={uid(u)}>
                  <td>
                    <input
                      aria-label={`选择 ${nick(u)}`}
                      type="checkbox"
                      checked={selected.includes(uid(u))}
                      onChange={(e) =>
                        setSelected((s) =>
                          e.target.checked
                            ? [...s, uid(u)]
                            : s.filter((v) => v !== uid(u)),
                        )
                      }
                    />
                  </td>
                  <td>{uid(u)}</td>
                  <td>
                    <Link to={`/user/${uid(u)}`}>{nick(u)}</Link>
                    <small> @{u.username || u.userName}</small>
                  </td>
                  <td>
                    <Badge tone={u.isDisable || u.status === 0 ? "orange" : ""}>
                      {u.isDisable || u.status === 0 ? "禁用" : "正常"}
                    </Badge>
                  </td>
                  <td>
                    <Button
                      tone="small"
                      action={() =>
                        batch(
                          u.isDisable || u.status === 0 ? "enable" : "disable",
                          [uid(u)],
                        )
                      }
                    >
                      {u.isDisable || u.status === 0 ? "启用" : "禁用"}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
      <Pagination
        page={page}
        size={20}
        total={request.data?.total || 0}
        onChange={setPage}
      />
    </section>
  );
}
function Reports() {
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
    <section className="panel">
      <h2>举报管理</h2>
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
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                {["ID", "目标", "原因", "举报人", "时间", "状态", "操作"].map(
                  (v) => (
                    <th key={v}>{v}</th>
                  ),
                )}
              </tr>
            </thead>
            <tbody>
              {rows(request.data).map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td>
                  <td className="wrap">
                    {typeName(p.targetType)} #{p.targetId}
                    <br />
                    {p.targetTitle}
                  </td>
                  <td className="wrap">{p.reason}</td>
                  <td>{p.reporterName}</td>
                  <td>{time(p.createTime)}</td>
                  <td>
                    <Badge tone={p.status === 0 ? "orange" : ""}>
                      {["待处理", "已处理", "已驳回"][p.status]}
                    </Badge>
                  </td>
                  <td>
                    <Button tone="small" action={() => setActive(String(p.id))}>
                      查看 / 处理
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
      <Pagination
        page={page}
        size={20}
        total={request.data?.total || 0}
        onChange={setPage}
      />
      {active && (
        <ReportDetail
          id={active}
          onClose={() => setActive(null)}
          reload={request.reload}
        />
      )}
    </section>
  );
}
function ReportDetail({
  id,
  onClose,
  reload,
}: {
  id: string;
  onClose: () => void;
  reload: () => void;
}) {
  const request = useData(`/report/detail/${id}`);
  const [status, setStatus] = useState("1"),
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
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
              >
                <option value="1">已处理（下架）</option>
                <option value="2">已驳回</option>
              </select>
            </Field>
            <Field label="处理说明" required>
              <textarea
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
            </Field>
            <div className="form-actions">
              <Button action={onClose}>取消</Button>
              <Button
                tone="primary"
                action={async () => {
                  if (!text.trim()) throw new Error("请填写处理说明");
                  await api(
                    url(`/report/handle/${id}`, { status, handleResult: text }),
                    "POST",
                  );
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
    <section className="panel">
      <div className="panel-title">
        <h2>公告管理</h2>
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
          新增公告
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
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                {["标题", "类型", "优先级", "状态", "发布时间", "操作"].map(
                  (v) => (
                    <th key={v}>{v}</th>
                  ),
                )}
              </tr>
            </thead>
            <tbody>
              {rows(request.data).map((p) => (
                <tr key={p.id}>
                  <td className="wrap">
                    {p.isTop === 1 && <Badge tone="orange">置顶</Badge>}{" "}
                    <Link to={`/notice/${p.id}`}>{p.title}</Link>
                  </td>
                  <td>{["", "系统公告", "活动通知", "重要提醒"][p.type]}</td>
                  <td>{["普通", "重要", "紧急"][p.priority]}</td>
                  <td>
                    <Badge>{["草稿", "已发布", "已下线"][p.status]}</Badge>
                  </td>
                  <td>{time(p.publishTime)}</td>
                  <td>
                    <div className="row-actions">
                      <Button
                        tone="small"
                        action={async () => {
                          const d = await api(`/notice/admin/${p.id}`);
                          setEditing({ ...p, ...d });
                        }}
                      >
                        编辑
                      </Button>
                      <Button
                        tone="small"
                        action={() =>
                          act(
                            `/notice/admin/${p.id}/${p.status === 1 ? "unpublish" : "publish"}`,
                            "PUT",
                          )
                        }
                      >
                        {p.status === 1 ? "下架" : "发布"}
                      </Button>
                      <Button
                        tone="small danger"
                        action={async () => {
                          if (confirm("确认删除公告？"))
                            await act(`/notice/admin/${p.id}`, "DELETE");
                        }}
                      >
                        删除
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataState>
      <Pagination
        page={page}
        size={20}
        total={request.data?.total || 0}
        onChange={setPage}
      />
      {editing && (
        <NoticeForm
          value={editing}
          onClose={() => setEditing(null)}
          reload={request.reload}
        />
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
        <input
          value={form.title}
          maxLength={100}
          onChange={(e) => f("title", e.target.value)}
        />
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
          <select
            value={form.type}
            onChange={(e) => f("type", Number(e.target.value))}
          >
            {["系统公告", "活动通知", "重要提醒"].map((v, i) => (
              <option key={i} value={i + 1}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        <Field label="优先级">
          <select
            value={form.priority}
            onChange={(e) => f("priority", Number(e.target.value))}
          >
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
            if (!form.title.trim() || !form.content?.trim())
              throw new Error("请填写标题和内容");
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
  const ref = useRef<HTMLDivElement>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let disposed = false;
    let chart: any;
    let observer: ResizeObserver;
    import("echarts")
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
            ["活跃用户", "dauList"],
            ["新增用户", "newUserList"],
            ["新增帖子", "newPostList"],
            ["新增评论", "newCommentList"],
            ["新增点赞", "newLikeList"],
          ].map(([name, key]) => ({
            name,
            type: "line",
            smooth: true,
            symbolSize: 5,
            data: data[key] || [],
          })),
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
  }, [data]);
  return error ? (
    <p role="alert">图表加载失败：{error}</p>
  ) : (
    <div
      ref={ref}
      role="img"
      aria-label="社区数据趋势折线图"
      style={{ height: 340, width: "100%" }}
    />
  );
}
function Statistics() {
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  const [type, setType] = useState("daily"),
    [date, setDate] = useState(today),
    [end, setEnd] = useState(today),
    [query, setQuery] = useState<Row>({ type: "daily", date: today }),
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
      d.activeUserCount ??
        d.dau ??
        d.totalActiveUsers ??
        d.avgDailyActiveUsers ??
        d.avgActiveUsers,
    ],
    [
      "新增帖子",
      d.totalNewPosts ??
        (d.newPetPostCount || 0) +
          (d.newActivityCount || 0) +
          (d.newDailyPostCount || 0),
    ],
    ["新增评论", d.newCommentCount ?? d.totalNewComments],
  ];
  return (
    <>
      <section className="panel">
        <div className="panel-title">
          <h2>今日实时</h2>
          <Button tone="small" action={realtime.reload}>
            刷新
          </Button>
        </div>
        <DataState request={realtime}>
          <div className="stat-grid">
            {[
              ["用户", r.newUserCount],
              [
                "帖子",
                (r.newPetPostCount || 0) +
                  (r.newActivityCount || 0) +
                  (r.newDailyPostCount || 0),
              ],
              ["评论", r.newCommentCount],
              ["点赞", r.newLikeCount],
              ["待审核", r.pendingAuditCount],
            ].map(([name, n]) => (
              <div className="stat-tile" key={name}>
                <strong><Value>{n ?? 0}</Value></strong>
                <span>{name}</span>
              </div>
            ))}
          </div>
        </DataState>
      </section>
      <section className="panel">
        <h2>统计概览</h2>
        <div className="toolbar">
          <select
            aria-label="统计周期"
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setDate(
                e.target.value === "monthly"
                  ? today.slice(0, 7)
                  : e.target.value === "yearly"
                    ? today.slice(0, 4)
                    : today,
              );
            }}
          >
            {[
              ["daily", "日统计"],
              ["weekly", "周统计"],
              ["monthly", "月统计"],
              ["yearly", "年统计"],
              ["range", "自定义区间"],
            ].map(([v, n]) => (
              <option key={v} value={v}>
                {n}
              </option>
            ))}
          </select>
          <input
            aria-label="统计日期"
            type={
              type === "monthly"
                ? "month"
                : type === "yearly"
                  ? "number"
                  : "date"
            }
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
          <Button action={() => setBackfill(true)}>补录数据</Button>
        </div>
        <DataState request={request}>
          <div className="stat-grid">
            {counters.map(([name, n]) => (
              <div className="stat-tile" key={name}>
                <strong><Value>{n ?? 0}</Value></strong>
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
            <input
              type="checkbox"
              checked={range}
              onChange={(e) => setRange(e.target.checked)}
            />
            区间补录
          </label>
          <Field label={range ? "开始日期" : "补录日期"}>
            <input
              type="date"
              value={start}
              onChange={(e) => setStart(e.target.value)}
            />
          </Field>
          {range && (
            <Field label="结束日期">
              <input
                type="date"
                value={finish}
                onChange={(e) => setFinish(e.target.value)}
              />
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
