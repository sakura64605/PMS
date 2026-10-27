import { useState } from "react";
import {
  ArrowRight,
  BookOpen,
  Calendar,
  CalendarCheck,
  Clock,
  Compass,
  Heart,
  MapPin,
  Megaphone,
  PawPrint,
  Plus,
  Sparkles,
  Users,
} from "lucide-react";
import { activityName, images, nick, rows, time, url, type Row } from "./api";
import { Avatar, Badge, DataState, Link, Modal, Pagination, useApp, useData } from "./core";
import { RouteImage } from "./RouteImage";

export function dashboardGreeting(date = new Date()) {
  const hour = Number(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: "Asia/Shanghai",
      hour: "2-digit",
      hourCycle: "h23",
    }).format(date),
  );
  return hour < 6 ? "夜深了" : hour < 12 ? "上午好" : hour < 18 ? "下午好" : "晚上好";
}
const entries = [
  {
    to: "/pets?type=0",
    title: "待领养萌宠",
    description: "猫咪/狗狗/兔兔健康档案与求领养",
    icon: Heart,
    tone: "sage",
  },
  {
    to: "/pets?type=2",
    title: "同城宠友活动",
    description: "周末草坪聚会、训犬交流与公益TNR",
    icon: Calendar,
    tone: "rose",
  },
  {
    to: "/daily",
    title: "宠友暖心日记",
    description: "记录养宠日常、新手答疑与好物分享",
    icon: BookOpen,
    tone: "sand",
  },
  {
    to: "/ai-chat",
    title: "AI 养宠顾问",
    description: "智能答疑、健康记忆与营养建议",
    icon: Sparkles,
    tone: "blue",
  },
];
export function Home() {
  const { user } = useApp();
  const [creating, setCreating] = useState(false);
  const [noticePage, setNoticePage] = useState(1);
  const notices = useData(url("/notice/list", { pageNum: noticePage, pageSize: 4 }));
  const unread = useData(user ? "/notice/unread-count" : null);
  const signups = useData(user ? "/activity/my-signups/summary" : null);
  const pets = useData("/pet/list?pageNum=1&pageSize=3&status=1");
  const activities = useData("/activity/list?pageNum=1&pageSize=2");
  const diaries = useData(user ? "/daily/feed?pageNum=1&pageSize=2" : null);
  return (
    <div className="dashboard-home">
      <section className="dashboard-welcome">
        <PawPrint className="dashboard-paw" size={180} aria-hidden="true" />
        <div className="dashboard-welcome-content">
          <p className="dashboard-eyebrow">
            <span />
            每一份陪伴，都值得被温柔以待
          </p>
          <h1>
            {dashboardGreeting()}，{user ? nick(user) : "新朋友"}
          </h1>
          <p className="dashboard-welcome-copy">
            欢迎{user ? "回到" : "来到"} PetCircle 宠友社！
            {pets.data && activities.data ? (
              <>
                社区有 <strong>{pets.data.total ?? rows(pets.data).length} 只</strong>{" "}
                等待关爱的伙伴与{" "}
                <strong>{activities.data.total ?? rows(activities.data).length} 场</strong>{" "}
                公开同城活动。
              </>
            ) : (
              "在这里遇见寻家伙伴，分享养宠日常，结识身边的宠友。"
            )}
            坚持领养代替购买，愿每一份纯粹的陪伴都被温柔以待。
          </p>
          <div className="row-actions">
            <Link className="btn primary" to="/pets?type=0">
              <Heart size={16} />
              浏览待领养宠物
            </Link>
            <Link className="btn dashboard-signups" to="/my-signups">
              <CalendarCheck size={16} />
              我的活动报名{signups.data ? ` (${signups.data.total})` : ""}
            </Link>
            <button className="btn" onClick={() => setCreating(true)}>
              <Plus size={16} />
              发起领养或活动
            </button>
          </div>
        </div>
      </section>
      <section className="dashboard-announcements">
        <header className="dashboard-announcement-heading">
          <div>
            <span className="dashboard-icon rose">
              <Megaphone size={16} />
            </span>
            <h2>社区公告与动态</h2>
            {Number(unread.data) > 0 && (
              <span className="dashboard-unread">{unread.data} 条未读</span>
            )}
          </div>
          <small>点击条目查看详细细则</small>
        </header>
        <DataState request={notices} empty={!!notices.data && !rows(notices.data).length}>
          <div>
            {rows(notices.data).map((n) => (
              <Link
                key={n.id}
                to={`/notice/${n.id}`}
                className={`dashboard-notice ${n.isRead ? "read" : "unread"}`}
              >
                <div>
                  {n.isTop === 1 && <Badge tone="orange">置顶</Badge>}
                  <span className={`dashboard-notice-type priority-${n.priority || 0}`}>
                    {["系统公告", "系统公告", "活动通知", "重要提醒"][n.type] || "社区公告"}
                  </span>
                  <strong>{n.title}</strong>
                </div>
                <span>
                  <time>{time(n.publishTime).slice(0, 10)}</time>
                  <ArrowRight size={16} />
                </span>
              </Link>
            ))}
          </div>
          {(notices.data?.total || 0) > 4 && (
            <Pagination
              page={noticePage}
              size={4}
              total={notices.data.total}
              onChange={setNoticePage}
            />
          )}
        </DataState>
      </section>
      <section className="dashboard-shortcuts" aria-label="社区快捷入口">
        {entries.map(({ to, title, description, icon: Icon, tone }) => (
          <Link key={to} to={to} className="dashboard-shortcut">
            <span className={`dashboard-icon ${tone}`}>
              <Icon size={20} />
            </span>
            <h3>{title}</h3>
            <p>{description}</p>
          </Link>
        ))}
      </section>
      <section className="dashboard-section">
        <DashboardHeading
          title="🐾 温暖守候 · 寻找爱心家庭"
          description="已通过社区内容审核，请进一步核实健康信息与送养条件"
          to="/pets"
          action="查看全部"
        />
        <DataState request={pets} empty={!!pets.data && !rows(pets.data).length}>
          <div className="dashboard-pets">
            {rows(pets.data).map((p) => (
              <Link className="dashboard-pet" key={p.id} to={`/pets/${p.id}`}>
                <div className="dashboard-pet-cover">
                  <RouteImage
                    src={images(p.images)[0] || "/favicon-paw.svg"}
                    alt={p.petName || p.title}
                    loading="lazy"
                  />
                  <span>{p.type === 1 ? "救助领养" : "个人送养"}</span>
                  {p.petType && <small>{p.petType}</small>}
                </div>
                <div className="dashboard-pet-body">
                  <div className="dashboard-pet-title">
                    <h3>{p.petName || p.title}</h3>
                    <small>
                      {p.petGender === 1 ? "公" : p.petGender === 2 ? "母" : "性别未知"} ·{" "}
                      {p.petAge || "年龄未知"}
                    </small>
                  </div>
                  <p className="dashboard-clamp">{p.content || p.title}</p>
                  <footer>
                    <span>
                      <MapPin size={14} />
                      {p.address || "地点未填写"}
                    </span>
                    <span>
                      {p.viewCount || 0} 浏览 · <b>{p.likeCount || 0} 赞</b>
                    </span>
                  </footer>
                </div>
              </Link>
            ))}
          </div>
        </DataState>
      </section>
      <section className="dashboard-section">
        <DashboardHeading
          title="🎉 同城宠友活动"
          description="结识身边的毛孩子家长，文明社交共同成长"
          to="/pets?type=2"
          action="全部活动"
        />
        <DataState request={activities} empty={!!activities.data && !rows(activities.data).length}>
          <div className="dashboard-activities">
            {rows(activities.data).map((a) => {
              const total = Math.max(0, Number(a.currentPeople) || 0),
                max = Math.max(0, Number(a.maxPeople) || 0),
                percent = max ? Math.min(100, Math.round((total / max) * 100)) : 0;
              return (
                <Link className="dashboard-activity" key={a.id} to={`/pets/activity/${a.id}`}>
                  <div className="dashboard-activity-top">
                    <Badge tone={a.status === 1 ? "orange" : ""}>{activityName(a.status)}</Badge>
                    <small>
                      <Clock size={14} />
                      {time(a.startTime).slice(0, 10)}
                    </small>
                  </div>
                  <h3>{a.title}</h3>
                  <p className="dashboard-clamp">{a.content}</p>
                  <div className="dashboard-progress">
                    <div>
                      <span>
                        <Users size={14} />
                        报名进度
                      </span>
                      <strong>
                        {total} / {max} 人 ({percent}%)
                      </strong>
                    </div>
                    <progress aria-label={`${a.title}报名进度`} value={percent} max={100} />
                  </div>
                  <footer>
                    <span>
                      <MapPin size={14} />
                      {a.location || "地点待公布"}
                    </span>
                    <b>查看详情与报名 →</b>
                  </footer>
                </Link>
              );
            })}
          </div>
        </DataState>
      </section>
      <section className="dashboard-diaries">
        <DashboardHeading
          title="📖 宠友日记精选"
          description="听听其他宠友与毛孩子的温暖日常与养宠心得"
          to="/daily"
          action="去宠友日记广场"
        />
        {user ? (
          <DataState request={diaries} empty={!!diaries.data && !rows(diaries.data).length}>
            <div className="dashboard-diary-grid">
              {rows(diaries.data).map((d) => (
                <Link className="dashboard-diary" key={d.id} to={`/daily/${d.id}`}>
                  <header>
                    <Avatar user={d.user} size={28} />
                    <strong>{nick(d.user)}</strong>
                    <small>· {time(d.createTime)}</small>
                  </header>
                  <p className="dashboard-clamp">{d.content}</p>
                  <div className="dashboard-topics">
                    {(d.topics || []).map((topic: Row | string, i: number) => (
                      <span key={typeof topic === "string" ? topic : topic.id || i}>
                        #{typeof topic === "string" ? topic : topic.name}
                      </span>
                    ))}
                  </div>
                </Link>
              ))}
            </div>
          </DataState>
        ) : (
          <div className="dashboard-guest-diary">
            <BookOpen size={28} />
            <p>登录后，发现宠友与毛孩子的温暖日常。</p>
            <Link className="btn primary" to="/login?next=%2Fdashboard">
              登录查看日记
            </Link>
          </div>
        )}
      </section>
      {creating && (
        <Modal title="发起一份温暖的分享" onClose={() => setCreating(false)}>
          <div className="dashboard-create-options">
            <Link to="/pets/create">
              <Heart size={22} />
              <div>
                <strong>发布领养 / 救助</strong>
                <p>为寻家伙伴寻找一份温暖</p>
              </div>
              <ArrowRight size={16} />
            </Link>
            <Link to="/pets/create?type=2">
              <Compass size={22} />
              <div>
                <strong>发起同城活动</strong>
                <p>邀请附近的宠友一起相聚</p>
              </div>
              <ArrowRight size={16} />
            </Link>
          </div>
        </Modal>
      )}
    </div>
  );
}
function DashboardHeading({
  title,
  description,
  to,
  action,
}: {
  title: string;
  description: string;
  to: string;
  action: string;
}) {
  return (
    <header className="dashboard-section-heading">
      <div>
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      <Link to={to}>
        {action}
        <ArrowRight size={14} />
      </Link>
    </header>
  );
}
