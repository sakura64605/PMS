import { useEffect, useRef, useState } from "react";
import {
  ArrowRight,
  Bookmark,
  CalendarDays,
  Eye,
  Heart,
  MapPin,
  MessageCircle,
  PawPrint,
  Plus,
  Users,
  Check,
  Copy,
  Flag,
  Megaphone,
} from "lucide-react";
import {
  api,
  copyText,
  url,
  rows,
  uid,
  nick,
  time,
  images,
  petName,
  activityName,
  auditName,
  canEdit,
  type Row,
} from "./api";
import {
  Author,
  Avatar,
  Back,
  Badge,
  Button,
  DataState,
  Empty,
  Field,
  Gallery,
  Intro,
  Link,
  Modal,
  Pagination,
  SearchForm,
  Upload,
  navigate,
  useApp,
  useData,
} from "./core";

export const contentRoute = (item: Row, activity = false) =>
  item.targetType === "daily" || item.type === "daily"
    ? `/daily/${item.targetId || item.id}`
    : activity || item.targetType === "activity" || item.type === "activity"
      ? `/pets/activity/${item.targetId || item.id}`
      : `/pets/${item.targetId || item.id}`;
export function Card({
  item,
  activity = false,
  children,
}: {
  item: Row;
  activity?: boolean;
  children?: React.ReactNode;
}) {
  const cover = images(item.images)[0] || item.coverImage;
  const to = contentRoute(item, activity);
  const author = item.user || {
    id: item.userId || item.posterId,
    nickname: item.posterName,
    avatar: item.posterAvatar,
  };
  return (
    <article className="pet-card">
      <Link to={to} className="cover" aria-label={item.title || "查看内容"}>
        {cover ? (
          <img src={cover} alt={item.title || "内容封面"} loading="lazy" />
        ) : (
          <PawPrint size={55} />
        )}
        <Badge tone={activity ? "blue" : item.type === 1 ? "orange" : ""}>
          {activity
            ? "同城活动"
            : item.type === "daily"
              ? "宠友日记"
              : item.type === 1
                ? "爱心救助"
                : item.type === "pet"
                  ? "领养 / 救助"
                  : "领养寻家"}
        </Badge>
      </Link>
      <div className="card-body">
        <Link to={to}>
          <h3>{item.title || "宠友分享"}</h3>
        </Link>
        {activity ? (
          <>
            <div className="card-line">
              <MapPin size={13} />
              {item.location || item.address || "地点未填写"}
            </div>
            <div className="card-line">
              <CalendarDays size={13} />
              {time(item.startTime || item.activityTime)}
            </div>
            <div className="card-line">
              <Users size={13} />
              {item.currentPeople ?? 0} /{" "}
              {item.maxPeople ?? item.maxParticipants ?? 0} 人{" "}
              <Badge tone="blue">{activityName(item.status)}</Badge>
            </div>
            <div className="progress">
              <span
                style={{
                  width: `${Math.min(100, (100 * (item.currentPeople || 0)) / Math.max(1, item.maxPeople || 1))}%`,
                }}
              />
            </div>
          </>
        ) : (
          <p className="summary">
            {[item.petName, item.petType, item.petAge]
              .filter(Boolean)
              .join(" · ") ||
              item.content ||
              "每一个小生命，都值得被温柔以待。"}
          </p>
        )}
        <div className="card-footer">
          <Author user={author} date={item.createTime} />
          <small>
            <Eye size={12} style={{ verticalAlign: "middle" }} />{" "}
            {item.viewCount || 0}　
            <Heart size={12} style={{ verticalAlign: "middle" }} />{" "}
            {item.likeCount || 0}
          </small>
        </div>
        {children && <div className="card-actions">{children}</div>}
      </div>
    </article>
  );
}
export function Home() {
  const { user } = useApp();
  const notices = useData("/notice/list?pageNum=1&pageSize=4"),
    pets = useData("/pet/list?pageNum=1&pageSize=3"),
    activities = useData("/activity/list?pageNum=1&pageSize=3");
  return (
    <>
      <section className="hero">
        <PawPrint size={180} className="hero-mark" />
        <p className="eyebrow">A LITTLE LOVE, A LIFETIME OF COMPANY</p>
        <h1>
          {user ? `欢迎回来，${nick(user)}` : "让每一份陪伴，都有温暖的归处"}
        </h1>
        <p>
          在宠友社，遇见等待新家的小伙伴，分享养宠生活里的小确幸。领养、互助、相聚，让每一次相遇都多一点爱。
        </p>
        <div className="row-actions">
          <Link className="btn primary" to="/pets?type=0">
            <Heart size={15} />
            遇见寻家伙伴
          </Link>
          <Link className="btn" to="/pets/create">
            <Plus size={15} />
            发布领养或活动
          </Link>
        </div>
      </section>
      <section className="panel">
        <div className="panel-title">
          <h2>
            <Megaphone
              size={17}
              style={{ verticalAlign: "middle", color: "#c37e63" }}
            />{" "}
            社区公告与动态
          </h2>
          <small>一起守护有爱的社区</small>
        </div>
        <DataState request={notices} empty={!rows(notices.data).length}>
          {rows(notices.data).map((n) => (
            <Link className="notice-row" to={`/notice/${n.id}`} key={n.id}>
              <div>
                <h3>
                  {n.isTop === 1 && <Badge tone="orange">置顶</Badge>}　
                  {n.title}
                </h3>
                <p className="muted">
                  {["", "系统公告", "活动通知", "重要提醒"][n.type]}
                  {n.priority > 0
                    ? " · " + ["普通", "重要", "紧急"][n.priority]
                    : ""}
                </p>
              </div>
              <small>{time(n.publishTime)}</small>
            </Link>
          ))}
        </DataState>
      </section>
      <div className="section-heading">
        <h2>等待一个温暖的新家</h2>
        <Link to="/pets">
          查看全部 <ArrowRight size={14} />
        </Link>
      </div>
      <DataState request={pets} skeleton="cards" empty={!rows(pets.data).length}>
        <div className="card-grid">
          {rows(pets.data).map((p) => (
            <Card key={p.id} item={p} />
          ))}
        </div>
      </DataState>
      <div className="section-heading">
        <h2>和毛孩子一起，去见见新朋友</h2>
        <Link to="/pets?type=2">
          同城活动 <ArrowRight size={14} />
        </Link>
      </div>
      <DataState request={activities} skeleton="cards" empty={!rows(activities.data).length}>
        <div className="card-grid">
          {rows(activities.data).map((p) => (
            <Card key={p.id} item={p} activity />
          ))}
        </div>
      </DataState>
    </>
  );
}
export function Pets() {
  const initial = new URLSearchParams(location.search).get("type") || "all";
  const [type, setType] = useState(initial),
    [page, setPage] = useState(1),
    [keyword, setKeyword] = useState(""),
    [filters, setFilters] = useState<Row>({});
  const activity = type === "2";
  const search = type === "all" && !!keyword;
  const path = search
    ? url("/search/global?keyword=" + encodeURIComponent(keyword), {
        pageNum: page,
        pageSize: 12,
        sortBy: filters.sortBy || "time",
      })
    : url(activity ? "/activity/list" : "/pet/list", {
        ...filters,
        type: type === "all" ? undefined : Number(type),
        keyword,
        pageNum: page,
        pageSize: 12,
      });
  const request = useData(path);
  const items = search
    ? request.data?.items || request.data?.results || []
    : rows(request.data);
  function filter(key: string, value: string) {
    setFilters((v) => ({ ...v, [key]: value }));
    setPage(1);
  }
  return (
    <>
      <Intro
        title="宠友圈 · 领养救助与同城活动"
        description="为毛孩子找到温暖港湾，也为彼此找到同行的伙伴。"
      >
        <Link
          to={activity ? "/pets/create?type=2" : "/pets/create"}
          className="btn primary"
        >
          <Plus size={15} /> {activity ? "发布活动" : "发布信息"}
        </Link>
      </Intro>
      <section className="panel">
        <div className="tabs">
          {[
            ["all", "全部"],
            ["0", "领养寻家"],
            ["1", "爱心救助"],
            ["2", "同城活动"],
          ].map(([v, t]) => (
            <button
              key={v}
              className={type === v ? "active" : ""}
              onClick={() => {
                setType(v);
                setPage(1);
                setFilters({});
              }}
            >
              {t}
            </button>
          ))}
        </div>
        <SearchForm
          onSearch={(s) => {
            setKeyword(s);
            setPage(1);
          }}
        >
          {activity ? (
            <>
              <input
                style={{ maxWidth: 150 }}
                value={filters.location || ""}
                onChange={(e) => filter("location", e.target.value)}
                placeholder="活动地点"
                aria-label="活动地点"
              />
              <select
                aria-label="活动状态"
                value={filters.status ?? ""}
                onChange={(e) => filter("status", e.target.value)}
              >
                <option value="">全部状态</option>
                <option value="0">报名中</option>
                <option value="1">进行中</option>
                <option value="2">已结束</option>
              </select>
            </>
          ) : (
            <>
              <select
                aria-label="宠物分类"
                value={filters.petCategory ?? ""}
                onChange={(e) => filter("petCategory", e.target.value)}
              >
                <option value="">全部宠物</option>
                {[
                  "猫",
                  "狗",
                  "兔子",
                  "啮齿类",
                  "鸟",
                  "鱼",
                  "爬行动物",
                  "其他",
                ].map((n, i) => (
                  <option value={i} key={i}>
                    {n}
                  </option>
                ))}
              </select>
              <select
                aria-label="性别"
                value={filters.gender ?? ""}
                onChange={(e) => filter("gender", e.target.value)}
              >
                <option value="">全部性别</option>
                <option value="0">未知</option>
                <option value="1">公</option>
                <option value="2">母</option>
              </select>
            </>
          )}
          <select
            aria-label="排序"
            value={filters.orderBy || "createTime"}
            onChange={(e) => filter("orderBy", e.target.value)}
          >
            <option value="createTime">最新发布</option>
            <option value="viewCount">热门内容</option>
            {activity && <option value="startTime">开始时间</option>}
          </select>
          <Button
            tone="ghost"
            action={() => {
              setFilters({});
              setPage(1);
            }}
          >
            清除筛选
          </Button>
        </SearchForm>
      </section>
      <DataState request={request} skeleton="cards" empty={!items.length}>
        <div className="card-grid">
          {items.map((p: Row) => {
            const t = p.type || p.targetType;
            const a = activity || t === "activity";
            const normalized = search
              ? {
                  ...p,
                  ...p.extra,
                  id: p.targetId || p.id,
                  images: p.images || [p.coverImage].filter(Boolean),
                  user: p.user || {
                    userId: p.userId,
                    nickname: p.userName,
                    avatar: p.userAvatar,
                  },
                }
              : p;
            return <Card key={`${t}-${p.id}`} item={normalized} activity={a} />;
          })}
        </div>
      </DataState>
      <Pagination
        page={page}
        total={request.data?.total || 0}
        onChange={setPage}
      />
    </>
  );
}
export function Report({
  type,
  id,
  onClose,
}: {
  type: string;
  id: string;
  onClose: () => void;
}) {
  const [chosen, setChosen] = useState<string[]>([]),
    [other, setOther] = useState("");
  const { notify } = useApp();
  return (
    <Modal title="提交举报" onClose={onClose}>
      <p className="muted">请选择举报原因，我们会认真核查。</p>
      <div className="tag-list">
        {[
          "色情低俗",
          "暴力血腥",
          "虚假信息",
          "诈骗信息",
          "侵犯隐私",
          "垃圾广告",
          "人身攻击",
          "其他",
        ].map((r) => (
          <label className="inline-check" key={r}>
            <input
              type="checkbox"
              checked={chosen.includes(r)}
              onChange={() =>
                setChosen((c) =>
                  c.includes(r) ? c.filter((v) => v !== r) : [...c, r],
                )
              }
            />
            {r}
          </label>
        ))}
      </div>
      <Field label="补充说明">
        <textarea
          value={other}
          onChange={(e) => setOther(e.target.value)}
          maxLength={500}
        />
      </Field>
      <div className="form-actions">
        <Button action={onClose}>取消</Button>
        <Button
          tone="primary"
          disabled={!chosen.length && !other.trim()}
          action={async () => {
            await api("/report/submit", "POST", {
              targetType: type,
              targetId: id,
              reason: [...chosen, other].filter(Boolean).join("; "),
            });
            notify("举报已提交");
            onClose();
          }}
        >
          提交举报
        </Button>
      </div>
    </Modal>
  );
}
export function Comments({ id, type }: { id: string; type: string }) {
  const [page, setPage] = useState(1),
    [text, setText] = useState(""),
    [reply, setReply] = useState<Row | null>(null),
    [report, setReport] = useState<string | null>(null);
  const request = useData(
    url("/comment/list", {
      targetId: id,
      targetType: type,
      pageNum: page,
      pageSize: 20,
    }),
  );
  const { user, notify } = useApp();
  async function submit() {
    if (!user) {
      navigate("/login");
      return;
    }
    if (!text.trim()) throw new Error("请输入评论内容");
    await api("/comment/create", "POST", {
      targetId: id,
      targetType: type,
      content: text.trim(),
      parentId: reply?.rootId || reply?.id,
      replyTo: reply?.user ? uid(reply.user) : undefined,
    });
    setText("");
    setReply(null);
    request.reload();
    notify("评论已提交");
  }
  function comment(c: Row, root?: string) {
    return (
      <article className="comment" key={c.id}>
        <Avatar user={c.user} size={32} />
        <div className="comment-body">
          <div className="comment-head">
            <strong>
              {nick(c.user)}
              {c.replyTo?.nickname ? ` → ${c.replyTo.nickname}` : ""}
            </strong>
            <small>{time(c.createTime)}</small>
          </div>
          <p className="prose">{c.content}</p>
          <div className="row-actions">
            <Button
              tone="ghost small"
              action={() => setReply({ ...c, rootId: root })}
            >
              回复
            </Button>
            <Button
              tone="ghost small"
              action={async () => {
                await api("/like", "POST", {
                  targetType: "pet_comment",
                  targetId: c.id,
                });
                request.reload();
              }}
            >
              <Heart size={13} />
              {c.likeCount || 0}
            </Button>
            <Button tone="ghost small" action={() => setReport(String(c.id))}>
              举报
            </Button>
          </div>
          {c.replies?.length > 0 && (
            <div className="replies">
              {c.replies.map((r: Row) => comment(r, String(c.id)))}
            </div>
          )}
        </div>
      </article>
    );
  }
  return (
    <section className="panel" id="comments">
      <div className="panel-title">
        <h2>
          聊一聊 · {request.data?.total || rows(request.data).length} 条评论
        </h2>
        {reply && (
          <Button tone="ghost small" action={() => setReply(null)}>
            取消回复 {nick(reply.user)}
          </Button>
        )}
      </div>
      <form
        className="comment-form"
        onSubmit={(e) => {
          e.preventDefault();
        }}
      >
        <textarea
          value={text}
          maxLength={2000}
          onChange={(e) => setText(e.target.value)}
          placeholder={
            reply ? `回复 ${nick(reply.user)}…` : "留下你的关心与想法…"
          }
          aria-label="评论内容"
        />
        <Button tone="primary" action={submit}>
          发送
        </Button>
      </form>
      <DataState request={request}>
        {rows(request.data).length ? (
          rows(request.data).map((c) => comment(c))
        ) : (
          <p className="muted">还没有评论，来和宠友聊聊吧。</p>
        )}
      </DataState>
      {request.data?.total > 20 && (
        <Pagination
          page={page}
          size={20}
          total={request.data.total}
          onChange={setPage}
        />
      )}
      {report && (
        <Report id={report} type="comment" onClose={() => setReport(null)} />
      )}
    </section>
  );
}
export function Detail({
  id,
  activity = false,
}: {
  id: string;
  activity?: boolean;
}) {
  const request = useData(activity ? `/activity/detail/${id}` : `/pet/${id}`);
  const { user, notify } = useApp();
  const [report, setReport] = useState(false),
    [signup, setSignup] = useState(false),
    [signups, setSignups] = useState(false);
  const p = request.data || {},
    owner = uid(user) === uid(p.user),
    ended = p.status === 2;
  const target = activity ? "pet_activity" : "pet_post";
  async function like() {
    const result = await api("/like", "POST", { targetId: id, targetType: target });
    request.update((old: Row) => ({...old, isLiked: result.isLiked, likeCount: result.likeCount}));
  }
  const mutate = async (path: string) => {
    await api(path, "POST");
    request.reload();
    notify("操作成功");
  };
  return (
    <>
      <Back />
      <DataState
        request={request}
        skeleton="detail"
        empty={!request.data || p.title === "宠物信息暂时不可用"}
      >
        <div className="detail-layout">
          <div>
            <article className="panel">
              <Author user={p.user} date={p.createTime} />
              <h1 className="detail-title">{p.title}</h1>
              <div className="row-actions">
                <Badge tone={activity ? "blue" : ""}>
                  {activity ? activityName(p.status) : petName(p.status)}
                </Badge>
                {p.auditStatus !== undefined && (
                  <Badge tone="neutral">{auditName(p.auditStatus)}</Badge>
                )}
                <small>
                  发布于 {time(p.createTime)}{" "}
                  {p.updateTime && `· 更新于 ${time(p.updateTime)}`}
                </small>
              </div>
              <Gallery value={images(p.images)} />
              <div className="facts">
                {activity ? (
                  <>
                    <div>
                      <small>活动地点</small>
                      <strong>{p.location || p.address}</strong>
                    </div>
                    <div>
                      <small>参与人数</small>
                      <strong>
                        {p.currentPeople || 0} / {p.maxPeople || 0}
                      </strong>
                    </div>
                    <div>
                      <small>活动时间</small>
                      <strong>
                        {time(p.startTime)}
                        <br />
                        {time(p.endTime)}
                      </strong>
                    </div>
                  </>
                ) : (
                  <>
                    <div>
                      <small>宠物名字</small>
                      <strong>{p.petName || "未填写"}</strong>
                    </div>
                    <div>
                      <small>品种 / 年龄</small>
                      <strong>
                        {p.petType || "未知"} · {p.petAge || "未知"}
                      </strong>
                    </div>
                    <div>
                      <small>性别</small>
                      <strong>{["未知", "公", "母"][p.petGender || 0]}</strong>
                    </div>
                  </>
                )}
              </div>
              <h2>关于{activity ? "这次相聚" : "这位小伙伴"}</h2>
              <p className="prose">{p.content}</p>
              <small>
                <Eye size={13} style={{ verticalAlign: "middle" }} />{" "}
                {p.viewCount || 0} 次浏览
              </small>
            </article>
            <Comments key={`${target}-${id}`} id={id} type={target} />
          </div>
          <aside className="detail-sidebar">
            <section className="panel">
              <h2>{activity ? "一起赴约" : "把陪伴带回家"}</h2>
              <p className="muted">每一次认真沟通，都是温暖关系的开始。</p>
              {[
                ["电话", p.contactPhone],
                ["微信", p.contactWechat],
                ["地址", p.address || p.location],
              ].map(
                ([label, value]) =>
                  value && (
                    <div key={label} className="contact-line">
                      <div>
                        <small>{label}</small>
                        <div>{value}</div>
                      </div>
                      {label !== "地址" && (
                        <Button
                          tone="ghost small"
                          aria-label={`复制${label}`}
                          action={async () => {
                            await copyText(value);
                            notify("已复制");
                          }}
                        >
                          <Copy size={14} />
                        </Button>
                      )}
                    </div>
                  ),
              )}
              <div className="row-actions">
                <Button tone={p.isLiked ? "primary" : ""} action={like}>
                  <Heart size={15} />
                  {p.isLiked ? "已点赞" : "点赞"} {p.likeCount || 0}
                </Button>
                {!activity && (
                  <Button
                    action={async () => {
                      await api(`/pet/${id}/collect`, "POST");
                      request.reload();
                    }}
                  >
                    <Bookmark size={15} />
                    {p.isFavorite ? "已收藏" : "收藏"}
                  </Button>
                )}
              </div>
              {activity && !owner && (
                <Button
                  tone="primary"
                  disabled={ended}
                  action={() => setSignup(true)}
                >
                  {ended
                    ? "活动已结束"
                    : p.isSignUp === 1
                      ? "查看报名信息"
                      : "报名参加"}
                </Button>
              )}
              {activity && p.isSignUp === 1 && !ended && (
                <Button action={() => mutate(`/activity/cancelSignUp/${id}`)}>
                  取消报名
                </Button>
              )}
              {owner && (
                <div className="row-actions">
                  {canEdit(p) && (
                    <Link
                      className="btn"
                      to={
                        activity
                          ? `/pets/create?type=2&id=${id}`
                          : `/pets/${id}/edit`
                      }
                    >
                      编辑
                    </Link>
                  )}
                  {!activity && p.status === 1 && (
                    <Button action={() => mutate(url("/pet/complete", { id }))}>
                      标记完成
                    </Button>
                  )}
                  {!ended && (
                    <Button
                      tone="danger"
                      action={async () => {
                        if (confirm("确定下架或删除这条内容吗？")) {
                          await mutate(
                            activity
                              ? `/activity/delete/${id}`
                              : url("/pet/delete", { id }),
                          );
                        }
                      }}
                    >
                      下架 / 删除
                    </Button>
                  )}
                  {activity && (
                    <Button action={() => setSignups(true)}>查看报名人</Button>
                  )}
                </div>
              )}
              {user && !owner && (
                <Button
                  tone="ghost"
                  action={async () => {
                    await api(url("/follow", { userId: uid(p.user) }), "POST");
                    request.reload();
                    notify("关注状态已更新");
                  }}
                >
                  {p.user?.isFollow ? "已关注" : "关注发布者"}
                </Button>
              )}
              <Button tone="ghost" action={() => setReport(true)}>
                <Flag size={14} />
                举报
              </Button>
              {user?.role === 1 && p.auditStatus === 0 && !ended && (
                <AuditActions
                  id={id}
                  type={activity ? "activity" : p.type === 1 ? "help" : "adopt"}
                  reload={request.reload}
                />
              )}
            </section>
          </aside>
        </div>
      </DataState>
      {report && (
        <Report
          type={activity ? "activity" : "pet"}
          id={id}
          onClose={() => setReport(false)}
        />
      )}
      {signup && (
        <Signup
          id={id}
          onClose={() => setSignup(false)}
          reload={request.reload}
        />
      )}
      {signups && <Signups id={id} onClose={() => setSignups(false)} />}
    </>
  );
}
export function AuditActions({
  id,
  type,
  reload,
}: {
  id: string;
  type: string;
  reload: () => void;
}) {
  const [reason, setReason] = useState<string | null>(null);
  const { notify } = useApp();
  async function decide(reject = false) {
    if (reject && !reason?.trim()) throw new Error("请输入拒绝理由");
    await api(
      url(`/admin/audit/batch-${reject ? "reject" : "approve"}`, {
        targetType: type,
      }),
      "POST",
      { ids: [id], rejectReason: reason },
    );
    setReason(null);
    reload();
    notify("审核已处理");
  }
  return (
    <>
      <div className="row-actions">
        <Button tone="primary" action={() => decide()}>
          通过审核
        </Button>
        <Button tone="danger" action={() => setReason("")}>
          拒绝
        </Button>
      </div>
      {reason !== null && (
        <Modal title="拒绝审核" onClose={() => setReason(null)}>
          <Field label="拒绝理由" required>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </Field>
          <div className="form-actions">
            <Button action={() => setReason(null)}>取消</Button>
            <Button tone="danger" action={() => decide(true)}>
              确认拒绝
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
export function Signup({
  id,
  onClose,
  reload,
}: {
  id: string;
  onClose: () => void;
  reload: () => void;
}) {
  const [form, set] = useState<Row>({ realName: "", phone: "", remark: "" });
  return (
    <Modal title="报名活动" onClose={onClose}>
      <form onSubmit={(e) => e.preventDefault()}>
        {[
          ["realName", "真实姓名"],
          ["phone", "联系电话"],
          ["remark", "备注"],
        ].map(([key, label]) => (
          <Field key={key} label={label} required={key !== "remark"}>
            <input
              value={form[key]}
              onChange={(e) => set((v) => ({ ...v, [key]: e.target.value }))}
            />
          </Field>
        ))}
        <div className="form-actions">
          <Button action={onClose}>取消</Button>
          <Button
            tone="primary"
            action={async () => {
              if (!form.realName.trim() || !/^1[3-9]\d{9}$/.test(form.phone))
                throw new Error("请填写真实姓名和有效手机号");
              await api("/activity/signUp", "POST", {
                ...form,
                activityId: id,
              });
              reload();
              onClose();
            }}
          >
            确认报名
          </Button>
        </div>
      </form>
    </Modal>
  );
}
export function Signups({ id, onClose }: { id: string; onClose: () => void }) {
  const [page, setPage] = useState(1),
    [data, setData] = useState<any>(null),
    [error, setError] = useState(""),
    [revision, setRevision] = useState(0);
  useEffect(() => {
    let alive = true;
    api(
      url(`/activity/signUpList/${id}`, { pageNum: page, pageSize: 20 }),
      "POST",
    )
      .then((v) => {
        if (alive) setData(v);
      })
      .catch((e) => {
        if (alive) setError(e.message);
      });
    return () => {
      alive = false;
    };
  }, [id, page, revision]);
  return (
    <Modal title="活动报名人" onClose={onClose}>
      {error && <p role="alert">{error}</p>}
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              {["昵称", "真实姓名", "电话", "备注", "报名时间", "状态"].map(
                (t) => (
                  <th key={t}>{t}</th>
                ),
              )}
            </tr>
          </thead>
          <tbody>
            {rows(data).map((p) => (
              <tr key={p.id || uid(p.user)}>
                <td>{nick(p.user)}</td>
                <td>{p.realName}</td>
                <td>{p.phone}</td>
                <td>{p.remark}</td>
                <td>{time(p.signupTime)}</td>
                <td>
                  <Button
                    disabled={p.isCheckedIn}
                    action={async () => {
                      await api(
                        url("/activity/signIn", {
                          activityId: id,
                          userId: uid(p.user) || p.userId,
                        }),
                        "POST",
                      );
                      setRevision((v) => v + 1);
                    }}
                  >
                    {p.isCheckedIn ? "已签到" : "签到"}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data && !rows(data).length && <Empty text="暂无报名记录" />}
      <Pagination
        page={page}
        size={20}
        total={data?.total || 0}
        onChange={setPage}
      />
    </Modal>
  );
}
export function Editor({
  id,
  activity = false,
}: {
  id?: string;
  activity?: boolean;
}) {
  const { notify } = useApp();
  const request = useData(
    id ? (activity ? `/activity/detail/${id}` : `/pet/${id}`) : null,
  );
  const [form, set] = useState<Row>({
    type: 0,
    title: "",
    content: "",
    petName: "",
    petType: "猫",
    petAge: "",
    petGender: 0,
    address: "",
    contactPhone: "",
    contactWechat: "",
    location: "",
    maxPeople: 20,
    startTime: "",
    endTime: "",
    images: [],
  });
  const initialized = useRef(false);
  useEffect(() => {
    if (request.data && !initialized.current) {
      initialized.current = true;
      set((v) => ({
        ...v,
        ...request.data,
        images: images(request.data.images),
      }));
    }
  }, [request.data]);
  const update = (key: string, v: any) => set((p) => ({ ...p, [key]: v }));
  async function save() {
    if (!form.title.trim() || !form.content.trim())
      throw new Error("请填写标题与详细描述");
    if (activity) {
      if (!form.location || !form.startTime || !form.endTime)
        throw new Error("请填写地点和活动时间");
      if (
        new Date(form.startTime) < new Date() ||
        new Date(form.endTime) <= new Date(form.startTime)
      )
        throw new Error("请检查活动起止时间");
    } else if (!form.address || !/^1[3-9]\d{9}$/.test(form.contactPhone))
      throw new Error("请填写地址和有效联系电话");
    if (id && !canEdit(request.data || {}))
      throw new Error("已完成或已结束内容不能修改");
    const payload = activity
      ? {
          id,
          title: form.title,
          content: form.content,
          images: form.images,
          location: form.location,
          maxPeople: Number(form.maxPeople),
          startTime:
            form.startTime.length === 16
              ? form.startTime + ":00"
              : form.startTime,
          endTime:
            form.endTime.length === 16 ? form.endTime + ":00" : form.endTime,
        }
      : {
          id,
          type: Number(form.type),
          title: form.title,
          content: form.content,
          images: form.images,
          petName: form.petName,
          petType: form.petType,
          petAge: form.petAge,
          petGender: Number(form.petGender),
          address: form.address,
          contactPhone: form.contactPhone,
          contactWechat: form.contactWechat,
        };
    await api(
      activity
        ? id
          ? `/activity/update/${id}`
          : "/activity/post"
        : id
          ? "/pet/update"
          : "/pet/post",
      "POST",
      payload,
    );
    notify("已保存，请等待审核");
    navigate("/pets/my-posts");
  }
  const input = (
    key: string,
    label: string,
    type = "text",
    required = false,
  ) => (
    <Field label={label} required={required}>
      <input
        type={type}
        required={required}
        value={form[key] ?? ""}
        maxLength={key === "title" ? 100 : undefined}
        min={type === "number" ? 1 : undefined}
        onChange={(e) => update(key, e.target.value)}
      />
    </Field>
  );
  if (id && (request.loading || request.error))
    return <DataState request={request}>{null}</DataState>;
  if (id && request.data && !canEdit(request.data))
    return (
      <>
        <Back to="/pets/my-posts" />
        <Empty text="已完成或已结束的内容不能修改" />
      </>
    );
  return (
    <div className="form-narrow">
      <Back to="/pets/my-posts" />
      <Intro
        title={`${id ? "编辑" : "发布"}${activity ? "同城活动" : "领养 / 救助信息"}`}
        description="把信息填写完整，让每一次沟通都更安心。"
      />
      <form className="panel" onSubmit={(e) => e.preventDefault()}>
        {!activity && (
          <Field label="发布类型">
            <select
              value={form.type}
              onChange={(e) => update("type", e.target.value)}
            >
              <option value="0">领养寻家</option>
              <option value="1">爱心救助</option>
            </select>
          </Field>
        )}
        {input("title", "标题", "text", true)}
        <div className="form-grid">
          {activity ? (
            <>
              {input("startTime", "开始时间", "datetime-local", true)}
              {input("endTime", "结束时间", "datetime-local", true)}
              {input("location", "活动地点", "text", true)}
              {input("maxPeople", "最大参与人数", "number", true)}
            </>
          ) : (
            <>
              {input("petName", "宠物名字")}
              <Field label="宠物品种">
                <input
                  value={form.petType}
                  onChange={(e) => update("petType", e.target.value)}
                  list="pet-breeds"
                />
                <datalist id="pet-breeds">
                  {["猫", "狗", "兔子", "仓鼠", "其他"].map((v) => (
                    <option key={v}>{v}</option>
                  ))}
                </datalist>
              </Field>
              {input("petAge", "年龄")}
              <Field label="性别">
                <select
                  value={form.petGender}
                  onChange={(e) => update("petGender", e.target.value)}
                >
                  <option value="0">未知</option>
                  <option value="1">公</option>
                  <option value="2">母</option>
                </select>
              </Field>
            </>
          )}
        </div>
        <Field label="图片">
          <Upload value={form.images} onChange={(v) => update("images", v)} />
        </Field>
        <Field label="详细描述" required>
          <textarea
            value={form.content}
            onChange={(e) => update("content", e.target.value)}
            rows={6}
            maxLength={20000}
          />
        </Field>
        {!activity && (
          <>
            {input("address", "所在地址", "text", true)}
            <div className="form-grid">
              {input("contactPhone", "联系电话", "tel", true)}
              {input("contactWechat", "微信号")}
            </div>
          </>
        )}
        <div className="form-actions">
          <Link to="/pets/my-posts" className="btn">
            取消
          </Link>
          <Button tone="primary" action={save}>
            {id ? "保存修改" : "提交发布"}
          </Button>
        </div>
      </form>
    </div>
  );
}
export function MyContent({
  mode = "posts",
}: {
  mode?: "posts" | "collections" | "recycle";
}) {
  const [activity, setActivity] = useState(false),
    [page, setPage] = useState(1),
    [signups, setSignups] = useState<string | null>(null);
  const { notify } = useApp();
  const path =
    mode === "collections"
      ? "/pet/favoriteList"
      : mode === "recycle"
        ? activity
          ? "/activity/recycle-bin"
          : "/pet/recycle-bin"
        : activity
          ? "/activity/myActivity"
          : "/pet/my-posts";
  const request = useData(url(path, { pageNum: page, pageSize: 12 }));
  const mutate = async (path: string, method = "POST") => {
    await api(path, method);
    request.reload();
    notify("操作成功");
  };
  return (
    <>
      <Intro
        title={
          mode === "posts"
            ? "我的发布"
            : mode === "collections"
              ? "珍藏的小伙伴"
              : "回收站"
        }
        description={
          mode === "recycle"
            ? "找回曾经的分享，或与它认真告别。"
            : "记录每一次分享，也记录每一份关心。"
        }
      >
        <Link
          to={activity ? "/pets/create?type=2" : "/pets/create"}
          className="btn primary"
        >
          <Plus size={14} />
          发布
        </Link>
      </Intro>
      {mode !== "collections" && (
        <div className="tabs">
          {[false, true].map((v) => (
            <button
              key={String(v)}
              className={activity === v ? "active" : ""}
              onClick={() => {
                setActivity(v);
                setPage(1);
              }}
            >
              {v ? "我的活动" : "领养 / 救助"}
            </button>
          ))}
        </div>
      )}
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="card-grid">
          {rows(request.data).map((p) => (
            <Card key={p.id} item={p} activity={activity}>
              <Badge tone={p.auditStatus === 0 ? "orange" : "neutral"}>
                {mode === "recycle" ? "已删除" : auditName(p.auditStatus)} ·{" "}
                {activity ? activityName(p.status) : petName(p.status)}
              </Badge>
              <Link
                className="btn small"
                to={activity ? `/pets/activity/${p.id}` : `/pets/${p.id}`}
              >
                查看
              </Link>
              {mode === "collections" ? (
                <Button
                  tone="small"
                  action={() => mutate(`/pet/${p.id}/collect`)}
                >
                  取消收藏
                </Button>
              ) : mode === "recycle" ? (
                <>
                  <Button
                    tone="small"
                    action={() =>
                      mutate(
                        activity
                          ? `/activity/recover/${p.id}`
                          : url("/pet/recover", { id: p.id }),
                      )
                    }
                  >
                    恢复
                  </Button>
                  <Button
                    tone="small danger"
                    action={async () => {
                      if (confirm("彻底删除后无法恢复，确定吗？"))
                        await mutate(
                          activity
                            ? `/activity/deleteReally/${p.id}`
                            : url("/pet/delete-really", { id: p.id }),
                          activity ? "POST" : "DELETE",
                        );
                    }}
                  >
                    彻底删除
                  </Button>
                </>
              ) : (
                <>
                  {canEdit(p) && (
                    <Link
                      className="btn small"
                      to={
                        activity
                          ? `/pets/create?type=2&id=${p.id}`
                          : `/pets/${p.id}/edit`
                      }
                    >
                      编辑
                    </Link>
                  )}
                  {!activity && p.status === 1 && (
                    <Button
                      tone="small"
                      action={() => mutate(url("/pet/complete", { id: p.id }))}
                    >
                      完成
                    </Button>
                  )}
                  {activity && (
                    <Button
                      tone="small"
                      action={() => setSignups(String(p.id))}
                    >
                      查看报名人
                    </Button>
                  )}
                  {!activity && p.status === 1 && (
                    <Button
                      tone="small"
                      action={() => mutate(url("/pet/offline", { id: p.id }))}
                    >
                      下架
                    </Button>
                  )}
                  {!activity && [3, 4, 5].includes(p.status) && (
                    <Button
                      tone="small"
                      action={async () => {
                        const d = await api(`/pet/${p.id}`);
                        await mutateUpdate(d);
                        request.reload();
                      }}
                    >
                      重新送审
                    </Button>
                  )}
                  <Button
                    tone="small danger"
                    disabled={activity && p.status === 2}
                    action={async () => {
                      if (confirm("确定删除这条内容吗？"))
                        await mutate(
                          activity
                            ? `/activity/delete/${p.id}`
                            : url("/pet/delete", { id: p.id }),
                        );
                    }}
                  >
                    删除
                  </Button>
                </>
              )}
            </Card>
          ))}
        </div>
      </DataState>
      <Pagination
        page={page}
        total={request.data?.total || 0}
        onChange={setPage}
      />
      {signups && <Signups id={signups} onClose={() => setSignups(null)} />}
    </>
  );
}
async function mutateUpdate(d: Row) {
  await api("/pet/update", "POST", { ...d, id: d.id });
}
export function Notice({ id }: { id: string }) {
  const request = useData(`/notice/${id}`);
  const n = request.data;
  return (
    <div className="form-narrow">
      <Back to="/dashboard" />
      <DataState request={request}>
        <article className="panel">
          <div className="row-actions">
            {n?.isTop === 1 && <Badge tone="orange">置顶</Badge>}
            <Badge>{["", "系统公告", "活动通知", "重要提醒"][n?.type]}</Badge>
            {n?.priority > 0 && (
              <Badge tone="orange">
                {["普通", "重要", "紧急"][n.priority]}
              </Badge>
            )}
          </div>
          <h1 className="detail-title">{n?.title}</h1>
          <p className="muted">发布于 {time(n?.publishTime)}</p>
          <hr
            style={{
              border: 0,
              borderTop: "1px solid #ece5db",
              margin: "24px 0",
            }}
          />
          <div className="prose">{n?.content}</div>
        </article>
      </DataState>
    </div>
  );
}
