import { useEffect, useState } from "react";
import {
  ArrowRight,
  Heart,
  MapPin,
  MessageCircle,
  Plus,
  Share2,
  Sparkles,
  Bookmark,
} from "lucide-react";
import { api, copyText, url, rows, uid, nick, time, images, type Row } from "./api";
import {
  Avatar,
  Author,
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
import { Comments, Card, Report } from "./content";

export function Auth({ register = false }: { register?: boolean }) {
  const [form, set] = useState<Row>({
      account: "",
      password: "",
      userName: "",
      nickName: "",
      phone: "",
    }),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const { refreshUser } = useApp();
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const data = await api(
        register ? "/user/register" : "/user/login",
        "POST",
        register
          ? {
              userName: form.userName,
              nickName: form.nickName,
              phone: form.phone,
              password: form.password,
            }
          : { account: form.account, password: form.password },
      );
      if (data?.token) {
        localStorage.setItem("token", data.token.replace(/^Bearer\s+/i, ""));
        await refreshUser();
        const next = new URLSearchParams(location.search).get("next");
        navigate(
          next?.startsWith("/") && !next.startsWith("//") ? next : "/dashboard",
        );
      } else navigate("/login");
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-page">
      <aside className="auth-story">
        <Link to="/pets" className="brand">
          <span className="brand-icon">
            <Heart size={22} />
          </span>
          <div>
            <strong>PetCircle · 宠友社</strong>
            <small>温暖清爽的宠友互助社区</small>
          </div>
        </Link>
        <h1>
          每一次相遇，
          <br />
          都值得被温柔以待。
        </h1>
        <p>
          领养一个伙伴，分享一段日常。
          <br />
          和懂你的宠友，一起把生活过得毛茸茸。
        </p>
        <Heart className="hero-mark" size={190} />
      </aside>
      <section className="auth-form-wrap">
        <form className="auth-form" onSubmit={submit}>
          <p className="eyebrow">WELCOME TO PETCIRCLE</p>
          <h2>{register ? "开启你的宠友生活" : "很高兴，再次遇见你"}</h2>
          <p className="muted">
            {register
              ? "加入社区，让每一份陪伴都有回应。"
              : "登录宠友社，继续分享与陪伴。"}
          </p>
          {error && (
            <p className="inline-error" role="alert">
              {error}
            </p>
          )}
          {(register
            ? [
                ["userName", "用户名", "text"],
                ["nickName", "昵称（可选）", "text"],
                ["phone", "手机号", "tel"],
              ]
            : [["account", "用户名 / 手机号", "text"]]
          ).map(([key, label, type]) => (
            <Field key={key} label={label} required={key !== "nickName"}>
              <input
                autoComplete={
                  key === "account" || key === "userName"
                    ? "username"
                    : undefined
                }
                required={key !== "nickName"}
                type={type}
                value={form[key]}
                onChange={(e) => set((v) => ({ ...v, [key]: e.target.value }))}
              />
            </Field>
          ))}
          <Field label="密码" required>
            <input
              type="password"
              autoComplete={register ? "new-password" : "current-password"}
              minLength={6}
              required
              value={form.password}
              onChange={(e) => set((v) => ({ ...v, password: e.target.value }))}
            />
          </Field>
          <button className="btn primary" disabled={busy}>
            {busy ? "请稍候…" : register ? "创建账号" : "登录宠友社"}
            <ArrowRight size={15} />
          </button>
          <p className="auth-bottom">
            {register ? "已经有账号？" : "还没有账号？"}{" "}
            <Link to={register ? "/login" : "/register"}>
              {register ? "去登录" : "立即注册"}
            </Link>
          </p>
          <p className="auth-bottom">
            <Link to="/pets">先去社区逛逛 →</Link>
          </p>
        </form>
      </section>
    </div>
  );
}
export function DiaryCard({
  post,
  detail = false,
}: {
  post: Row;
  detail?: boolean;
}) {
  const { notify } = useApp();
  const [liked, setLiked] = useState(!!post.isLiked),
    [count, setCount] = useState(post.likeCount || 0);
  useEffect(() => {
    setLiked(!!post.isLiked);
    setCount(post.likeCount || 0);
  }, [post]);
  return (
    <article className="panel diary-card">
      <Author user={post.user} date={post.createTime} />
      <Link to={`/daily/${post.id}`}>
        <p className="prose">{post.content}</p>
      </Link>
      <Gallery value={images(post.images)} />
      {post.location && (
        <div className="card-line">
          <MapPin size={13} />
          {post.location}
        </div>
      )}
      <div className="tag-list">
        {post.topics?.map((t: Row) => (
          <Badge key={t.id}># {t.name}</Badge>
        ))}
      </div>
      <div className="card-footer">
        <div className="row-actions">
          <Button
            tone={liked ? "primary small" : "ghost small"}
            action={async () => {
              const result = await api(`/daily/${post.id}/like`, "POST");
              const next = typeof result === "boolean" ? result : !liked;
              setCount((n: number) =>
                Math.max(0, n + (next === liked ? 0 : next ? 1 : -1)),
              );
              setLiked(next);
            }}
          >
            <Heart size={14} />
            {count}
          </Button>
          <Link className="btn ghost small" to={`/daily/${post.id}#comments`}>
            <MessageCircle size={14} />
            {post.commentCount || 0}
          </Link>
        </div>
        <Button
          tone="ghost small"
          action={async () => {
            await copyText(
              location.origin + `/daily/${post.id}`,
            );
            notify("分享链接已复制");
          }}
        >
          <Share2 size={14} />
          分享
        </Button>
      </div>
    </article>
  );
}
export function Diaries({ id }: { id?: string }) {
  const [page, setPage] = useState(1);
  const request = useData(
    id ? `/daily/${id}` : url("/daily/feed", { pageNum: page, pageSize: 12 }),
  );
  return (
    <>
      <Intro
        title={id ? "宠友日记" : "生活的可爱，值得被记录"}
        description="分享毛孩子的小日常，收藏平凡生活里的小确幸。"
      >
        <Link to="/daily/publish" className="btn primary">
          <Plus size={14} />
          发布日记
        </Link>
      </Intro>
      <div className="diary-list">
        {id && <Back to="/daily" />}
        <DataState request={request} empty={!id && !rows(request.data).length}>
          {id
            ? request.data && <DiaryCard post={request.data} detail />
            : rows(request.data).map((p) => <DiaryCard post={p} key={p.id} />)}
        </DataState>
        {id && request.data ? (
          <Comments id={id} type="daily" />
        ) : (
          <Pagination
            page={page}
            total={request.data?.total || 0}
            onChange={setPage}
          />
        )}
      </div>
    </>
  );
}
export function PublishDiary() {
  const [content, setContent] = useState(""),
    [location, setLocation] = useState(""),
    [pics, setPics] = useState<string[]>([]),
    [topicIds, setTopicIds] = useState<number[]>([]),
    [keyword, setKeyword] = useState("");
  const topics = useData(
    keyword
      ? url("/daily/topics/search", { keyword })
      : "/daily/topics/hot?limit=20",
  );
  const { notify, user } = useApp();
  return (
    <div className="form-narrow">
      <Back to="/daily" />
      <Intro
        title="记录今天的小可爱"
        description="一张照片，一点碎碎念，都是珍贵的陪伴。"
      />
      <section className="panel">
        <Field label="想分享的日常" required>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={7}
            maxLength={20000}
            placeholder="今天，毛孩子又带来了什么小惊喜？"
          />
        </Field>
        <Field label="照片">
          <Upload value={pics} onChange={setPics} />
        </Field>
        <Field label="位置（可选）">
          <input
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="给回忆标记一个地点"
          />
        </Field>
        <Field label="话题" required>
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索话题"
          />
        </Field>
        <DataState request={topics}>
          <div className="tag-list">
            {rows(topics.data).map((t) => (
              <label className="inline-check" key={t.id}>
                <input
                  type="checkbox"
                  checked={topicIds.includes(t.id)}
                  onChange={() =>
                    setTopicIds((v) =>
                      v.includes(t.id)
                        ? v.filter((n) => n !== t.id)
                        : [...v, t.id],
                    )
                  }
                />
                # {t.name}
              </label>
            ))}
          </div>
        </DataState>
        {user?.role === 1 && (
          <Button
            tone="small"
            action={async () => {
              const name = prompt("话题名称");
              if (!name?.trim()) return;
              await api(
                url("/daily/topics/create", {
                  name: name.trim(),
                  description: "",
                }),
                "POST",
              );
              topics.reload();
            }}
          >
            创建话题
          </Button>
        )}
        <div className="form-actions">
          <Link className="btn" to="/daily">
            取消
          </Link>
          <Button
            tone="primary"
            action={async () => {
              if (!content.trim() || !topicIds.length)
                throw new Error("请填写正文并选择至少一个话题");
              await api("/daily/publish", "POST", {
                dailyPost: { content: content.trim(), images: pics, location },
                topicIds,
              });
              notify("日记已提交");
              navigate("/daily");
            }}
          >
            发布日记
          </Button>
        </div>
      </section>
    </div>
  );
}
export function Feed() {
  const [page, setPage] = useState(1);
  const request = useData(url("/feed/home", { pageNum: page, pageSize: 12 }));
  async function like(p: Row) {
    await api("/like", "POST", { targetType: p.postType === "activity" ? "pet_activity" : "pet_post", targetId: p.postId });
    request.reload();
  }
  return (
    <>
      <Intro
        title="关注动态"
        description="你关心的宠友，他们的每一份新分享。"
      />
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="card-grid">
          {rows(request.data).map((p) => (
            <Card
              key={`${p.postType}-${p.id}`}
              activity={p.postType === "activity"}
              item={{
                ...p,
                id: p.postId,
                user: {
                  id: p.posterId,
                  nickname: p.posterName,
                  avatar: p.posterAvatar,
                },
                images: p.coverImage ? [p.coverImage] : [],
              }}
            ><Button tone={p.isLiked ? "primary small" : "ghost small"} action={() => like(p)}><Heart size={14}/>{p.isLiked ? "已点赞" : "点赞"} {p.likeCount || 0}</Button></Card>
          ))}
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
export function Profile() {
  const { user, refreshUser, notify } = useApp();
  const [form, set] = useState<Row>({}),
    [modal, setModal] = useState<string | null>(null),
    [tags, setTags] = useState("");
  useEffect(() => {
    if (user) {
      set({ ...user, privacySettings: { ...user.privacySettings } });
      setTags((user.tags || []).join("，"));
    }
  }, [user]);
  if (!user) return null;
  const change = (k: string, v: any) => set((p) => ({ ...p, [k]: v }));
  return (
    <>
      <Intro title="个人中心" description="在这里，留下你和毛孩子的故事。" />
      <section className="profile-banner">
        <Avatar user={user} size={78} />
        <div>
          <h1>{nick(user)}</h1>
          <p className="muted">
            @{user.userName || user.username} · 加入于{" "}
            {time(user.createTime).slice(0, 10)}
          </p>
          <div className="profile-stats">
            <button className="btn ghost" onClick={() => setModal("follower")}>
              <strong>{user.followerCount || 0}</strong>
              <small>粉丝</small>
            </button>
            <button className="btn ghost" onClick={() => setModal("following")}>
              <strong>{user.followingCount || 0}</strong>
              <small>关注</small>
            </button>
            <span>
              <strong>{user.likeCount || 0}</strong>
              <small>获赞</small>
            </span>
          </div>
        </div>
      </section>
      <div className="two-column">
        <div>
          <section className="panel">
            <div className="panel-title">
              <h2>基本信息</h2>
              <small>认识独一无二的你</small>
            </div>
            <div className="form-grid">
              {[
                ["nickName", "昵称"],
                ["email", "邮箱"],
              ].map(([k, n]) => (
                <Field label={n} key={k}>
                  <input
                    value={form[k] || ""}
                    type={k === "email" ? "email" : "text"}
                    onChange={(e) => change(k, e.target.value)}
                  />
                </Field>
              ))}
              <Field label="手机">
                <input value={user.phone || "未设置"} disabled />
              </Field>
              <Field label="性别">
                <select
                  value={form.gender || 0}
                  onChange={(e) => change("gender", Number(e.target.value))}
                >
                  <option value="0">未知</option>
                  <option value="1">男</option>
                  <option value="2">女</option>
                </select>
              </Field>
            </div>
            <Field label="个性签名">
              <textarea
                value={form.signature || ""}
                onChange={(e) => change("signature", e.target.value)}
                maxLength={200}
              />
            </Field>
            <Field label="标签（用逗号分隔）">
              <input value={tags} onChange={(e) => setTags(e.target.value)} />
            </Field>
            <div className="form-actions">
              <Button
                action={() => {
                  set({
                    ...user,
                    privacySettings: { ...user.privacySettings },
                  });
                  setTags((user.tags || []).join("，"));
                }}
              >
                取消修改
              </Button>
              <Button
                tone="primary"
                action={async () => {
                  await api("/user/update", "PUT", {
                    nickName: form.nickName,
                    email: form.email,
                    gender: form.gender,
                    signature: form.signature,
                    tags: tags
                      .split(/[,，]/)
                      .map((t) => t.trim())
                      .filter(Boolean),
                    privacySettings: form.privacySettings,
                    searchable: form.searchable,
                  });
                  await refreshUser();
                  notify("资料已更新");
                }}
              >
                保存修改
              </Button>
            </div>
          </section>
          <section className="panel">
            <h2>账号安全</h2>
            <p className="muted">
              账号状态：{user.status === 1 ? "正常" : "禁用"}
            </p>
            <Button action={() => setModal("password")}>修改密码</Button>
          </section>
        </div>
        <aside>
          <section className="panel">
            <h2>头像设置</h2>
            <Upload
              value={[]}
              endpoint="/avatar/upload"
              max={1}
              onChange={async () => {
                await refreshUser();
              }}
            />
            <Button tone="ghost" action={() => setModal("avatar")}>
              查看历史头像
            </Button>
          </section>
          <section className="panel">
            <h2>隐私设置</h2>
            {[
              ["phone", "公开手机号"],
              ["email", "公开邮箱"],
              ["tags", "公开标签"],
            ].map(([k, n]) => (
              <label key={k} className="inline-check">
                <input
                  type="checkbox"
                  checked={!!form.privacySettings?.[k]}
                  onChange={(e) =>
                    change("privacySettings", {
                      ...form.privacySettings,
                      [k]: e.target.checked,
                    })
                  }
                />
                {n}
              </label>
            ))}
            <label className="inline-check">
              <input
                type="checkbox"
                checked={form.searchable === 1}
                onChange={(e) => change("searchable", e.target.checked ? 1 : 0)}
              />
              允许被用户名 / 手机号搜索
            </label>
            <small>随资料一同保存</small>
          </section>
          <section className="panel">
            <h2>我的小天地</h2>
            <div className="row-actions">
              <Link className="btn" to="/pets/my-posts">
                我的发布
              </Link>
              <Link className="btn" to="/pets/collections">
                我的收藏
              </Link>
              <Link className="btn" to="/recycle">
                回收站
              </Link>
            </div>
          </section>
        </aside>
      </div>
      {modal === "password" && <Password onClose={() => setModal(null)} />}
      {modal === "avatar" && <AvatarHistory onClose={() => setModal(null)} />}
      {(modal === "following" || modal === "follower") && (
        <People kind={modal} onClose={() => setModal(null)} />
      )}
    </>
  );
}
function Password({ onClose }: { onClose: () => void }) {
  const [f, set] = useState<Row>({
    oldPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const { notify } = useApp();
  return (
    <Modal title="修改密码" onClose={onClose}>
      {[
        ["oldPassword", "原密码"],
        ["newPassword", "新密码"],
        ["confirmPassword", "确认新密码"],
      ].map(([k, l]) => (
        <Field key={k} label={l} required>
          <input
            type="password"
            autoComplete={
              k === "oldPassword" ? "current-password" : "new-password"
            }
            value={f[k]}
            onChange={(e) => set((v) => ({ ...v, [k]: e.target.value }))}
          />
        </Field>
      ))}
      <div className="form-actions">
        <Button action={onClose}>取消</Button>
        <Button
          tone="primary"
          action={async () => {
            if (
              !f.oldPassword ||
              f.newPassword.length < 6 ||
              f.newPassword !== f.confirmPassword
            )
              throw new Error("新密码至少 6 位，且两次输入一致");
            await api("/user/changePassword", "POST", f);
            notify("密码已修改");
            onClose();
          }}
        >
          确认修改
        </Button>
      </div>
    </Modal>
  );
}
function AvatarHistory({ onClose }: { onClose: () => void }) {
  const request = useData("/avatar/historyAvatar");
  const { refreshUser } = useApp();
  return (
    <Modal title="历史头像" onClose={onClose}>
      <DataState request={request} empty={!rows(request.data).length}>
        <div className="gallery">
          {rows(request.data).map((p) => (
            <Button
              key={p.id}
              action={async () => {
                await api(`/avatar/switch/${p.id}`, "PUT");
                await refreshUser();
                onClose();
              }}
            >
              <img src={p.avatarUrl || p.avatar} alt="选择此头像" />
            </Button>
          ))}
        </div>
      </DataState>
    </Modal>
  );
}
function People({ kind, onClose }: { kind: string; onClose: () => void }) {
  const [page, setPage] = useState(1);
  const request = useData(
    url(`/${kind}/list`, { pageNum: page, pageSize: 20 }),
  );
  return (
    <Modal
      title={kind === "following" ? "我的关注" : "我的粉丝"}
      onClose={onClose}
    >
      <DataState request={request} empty={!rows(request.data).length}>
        {rows(request.data).map((p) => (
          <Link
            className="notice-row"
            to={`/user/${p.id}`}
            key={p.id}
            onClick={onClose}
          >
            <Avatar user={p} />
            <span>
              {nick(p)}
              <small> @{p.username}</small>
            </span>
          </Link>
        ))}
      </DataState>
      <Pagination
        page={page}
        size={20}
        total={request.data?.total || 0}
        onChange={setPage}
      />
    </Modal>
  );
}
export function UserPage({ id }: { id: string }) {
  const request = useData(url("/user/profileInfo", { userId: id }));
  const posts = useData(
    url("/pet/list", { userId: id, pageNum: 1, pageSize: 12 }),
  );
  const { user, notify } = useApp();
  const [report, setReport] = useState(false);
  const p = request.data || {},
    u = p.user || p;
  return (
    <>
      <Back />
      <DataState request={request}>
        <section className="profile-banner">
          <Avatar user={u} size={85} />
          <div>
            <h1>{nick(u)}</h1>
            <p className="muted">
              @{u.username || u.userName} ·{" "}
              {p.signature || "很高兴在这里遇见你"}
            </p>
            <div className="profile-stats">
              {[
                [p.followerCount, "粉丝"],
                [p.followingCount, "关注"],
                [p.likeCount, "获赞"],
              ].map(([v, n]) => (
                <span key={n}>
                  <strong>{v || 0}</strong>
                  <small>{n}</small>
                </span>
              ))}
            </div>
          </div>
        </section>
        <section className="panel">
          <div className="row-actions">
            <Badge>
              加入于 {time(p.joinTime || u.createTime).slice(0, 10)}
            </Badge>
            <Badge tone="neutral">最近活跃 {time(p.lastActiveTime)}</Badge>
            <Badge tone="neutral">{["未知", "男", "女"][p.gender || 0]}</Badge>
          </div>
          <div className="tag-list">
            {(p.tags || []).map((v: string) => (
              <Badge key={v}>{v}</Badge>
            ))}
          </div>
          {uid(user) !== String(id) && (
            <div className="row-actions">
              <Button
                tone="primary"
                action={async () => {
                  await api(url("/follow", { userId: id }), "POST");
                  request.reload();
                  notify("关注状态已更新");
                }}
              >
                {p.user?.isFollow || p.isFollow || p.isFollowing
                  ? "已关注"
                  : "关注"}
              </Button>
              <Button
                action={async () => {
                  const c = await api(
                    url("/message/private/conversation", { otherUserId: id }),
                  );
                  navigate(`/private-message?conversation=${c.conversationId}`);
                }}
              >
                发私信
              </Button>
              <Button tone="ghost" action={() => setReport(true)}>
                举报
              </Button>
            </div>
          )}
        </section>
        <h2>发布的宠物</h2>
        <DataState request={posts} empty={!rows(posts.data).length}>
          <div className="card-grid">
            {rows(posts.data).map((v) => (
              <Card item={v} key={v.id} />
            ))}
          </div>
        </DataState>
      </DataState>
      {report && (
        <Report id={id} type="user" onClose={() => setReport(false)} />
      )}
    </>
  );
}
const messageTypes = [
  ["", "全部"],
  ["LIKE", "点赞"],
  ["COMMENT", "评论"],
  ["FOLLOW", "关注"],
  ["SIGN_UP", "报名"],
  ["SIGN_IN", "签到"],
  ["ACTIVITY_REMINDER", "活动提醒"],
  ["ACTIVITY_FULL", "满员提醒"],
  ["AUDIT_PASS", "审核通过"],
  ["AUDIT_REJECT", "审核拒绝"],
  ["PUNISHMENT_START", "惩罚开始"],
  ["PUNISHMENT_END", "惩罚结束"],
  ["NO_SHOW_WARNING", "爽约警告"],
  ["ACTIVITY_STATISTICS", "活动统计报告"],
  ["NOTICE", "公告"],
  ["SYSTEM", "系统消息"],
];
export function Messages() {
  const [page, setPage] = useState(1),
    [type, setType] = useState("");
  const request = useData(
    url("/message/list", { pageNum: page, pageSize: 20, type }),
  );
  const { refresh, notify } = useApp();
  return (
    <>
      <Intro title="消息中心" description="每一份回应，都是来自社区的温暖。">
        <Button
          action={async () => {
            await api(url("/message/read-all", { type }), "PUT");
            request.reload();
            refresh();
            notify("已标记为已读");
          }}
        >
          全部标记已读
        </Button>
      </Intro>
      <section className="panel">
        <select
          aria-label="消息类型"
          value={type}
          onChange={(e) => {
            setType(e.target.value);
            setPage(1);
          }}
          style={{ maxWidth: 220 }}
        >
          {messageTypes.map(([v, n]) => (
            <option key={v} value={v}>
              {n}
            </option>
          ))}
        </select>
      </section>
      <DataState request={request} empty={!rows(request.data).length}>
        {rows(request.data).map((m) => (
          <article
            key={m.id}
            className={`message-row ${m.isRead ? "" : "unread"}`}
          >
            <span className="message-symbol">
              <MessageCircle size={20} />
            </span>
            <div style={{ flex: 1 }}>
              <h3>{m.title}</h3>
              <p>{m.content}</p>
              <small>{time(m.createTime)}</small>
              <div className="row-actions">
                <Button
                  tone="ghost small"
                  disabled={!!m.isRead}
                  action={async () => {
                    await api(`/message/read/${m.id}`, "PUT");
                    request.reload();
                    refresh();
                  }}
                >
                  标记已读
                </Button>
                {m.targetId && (
                  <Button
                    tone="ghost small"
                    action={async () => {
                      await api(`/message/read/${m.id}`, "PUT");
                      const t = m.targetType;
                      const route = ["activity", "pet_activity"].includes(t)
                        ? `/pets/activity/${m.targetId}`
                        : ["daily"].includes(t)
                          ? `/daily/${m.targetId}`
                          : ["notice", "announcement"].includes(t)
                            ? `/notice/${m.targetId}`
                            : t === "user"
                              ? `/user/${m.targetId}`
                              : `/pets/${m.targetId}`;
                      navigate(route);
                    }}
                  >
                    查看内容 →
                  </Button>
                )}
              </div>
            </div>
            {!m.isRead && <Badge>未读</Badge>}
          </article>
        ))}
      </DataState>
      <Pagination
        page={page}
        size={20}
        total={request.data?.total || 0}
        onChange={setPage}
      />
    </>
  );
}
