import { useState } from "react";
import {
  CalendarCheck,
  Compass,
  Clock,
  MapPin,
  Ticket,
  CheckCircle2,
  RotateCcw,
  Users,
} from "lucide-react";
import { api, url, rows, nick, time, activityName, type Row } from "./api";
import {
  Badge,
  Button,
  DataState,
  Empty,
  Field,
  Link,
  Modal,
  Pagination,
  RouteImage,
  SearchForm,
  useApp,
  useData,
} from "./core";

const signupName = (status: number) =>
  ({ 1: "已报名 · 待到场", 3: "已现场签到", 4: "未到场" })[status] || "未知状态";
export function MySignups() {
  const { notify } = useApp();
  const [filters, setFilters] = useState({
    keyword: "",
    status: "",
    signupStatus: "",
    timeRange: "",
    sort: "startTime_asc",
  });
  const [page, setPage] = useState(1);
  const [searchRevision, resetSearch] = useState(0);
  const [ticket, setTicket] = useState<Row | null>(null);
  const [cancel, setCancel] = useState<Row | null>(null);
  const request = useData(url("/activity/my-signups", { ...filters, pageNum: page, pageSize: 12 }));
  const summary = useData("/activity/my-signups/summary");
  const change = (key: keyof typeof filters, value: string) => {
    setFilters((old) => ({ ...old, [key]: value }));
    setPage(1);
  };
  const metrics = summary.data || {};
  return (
    <div className="my-signups">
      <section className="signup-hero">
        <div className="signup-hero-heading">
          <div>
            <small>
              <CalendarCheck size={16} /> 同城宠友聚会 · 陪伴足迹
            </small>
            <h1>我的报名 · 我参与的同城活动</h1>
            <p>集中管理您已报名的线下宠友聚会、公益义诊与萌宠沙龙，查看报名凭证与现场签到状态</p>
          </div>
          <Link className="btn primary" to="/pets?type=2">
            <Compass size={16} />
            探索更多同城活动
          </Link>
        </div>
        <DataState request={summary}>
          <div className="signup-metrics">
            {[
              ["累计报名活动", "total"],
              ["待参加 / 待到场", "pending"],
              ["已完成现场签到", "checkedIn"],
              ["往期活动记录", "finished"],
            ].map(([label, key]) => (
              <div key={key}>
                <small>{label}</small>
                <p>
                  <strong>{metrics[key] ?? "—"}</strong> 场
                </p>
              </div>
            ))}
          </div>
        </DataState>
      </section>
      <section className="panel signup-filters">
        <div className="signup-filter-heading">
          <SearchForm
            key={searchRevision}
            placeholder="搜索活动标题、举办地点、发起人、报名备注..."
            onSearch={(value) => change("keyword", value)}
          />
          <span>
            已为您筛选出 <strong>{request.data?.total ?? "—"}</strong> 场活动
          </span>
          <Button
            action={() => {
              setFilters({
                keyword: "",
                status: "",
                signupStatus: "",
                timeRange: "",
                sort: "startTime_asc",
              });
              setPage(1);
              resetSearch((v) => v + 1);
            }}
          >
            <RotateCcw size={14} />
            清空条件
          </Button>
        </div>
        <div className="signup-filter-grid">
          <Field label="活动进行状态">
            <select value={filters.status} onChange={(e) => change("status", e.target.value)}>
              <option value="">全部活动状态</option>
              <option value="0">报名中</option>
              <option value="1">正在进行中</option>
              <option value="2">已结束</option>
              <option value="3">已下架</option>
            </select>
          </Field>
          <Field label="我的签到状态">
            <select
              value={filters.signupStatus}
              onChange={(e) => change("signupStatus", e.target.value)}
            >
              <option value="">全部签到状态</option>
              <option value="1">已报名 · 待现场签到</option>
              <option value="3">已完成现场签到</option>
              <option value="4">未到场</option>
            </select>
          </Field>
          <Field label="活动时间范畴">
            <select value={filters.timeRange} onChange={(e) => change("timeRange", e.target.value)}>
              <option value="">全部时间</option>
              <option value="upcoming">近期即将参加</option>
              <option value="past">往期历史记录</option>
            </select>
          </Field>
          <Field label="智能多维度排序">
            <select value={filters.sort} onChange={(e) => change("sort", e.target.value)}>
              <option value="startTime_asc">活动开始时间：从早到晚</option>
              <option value="startTime_desc">活动开始时间：从晚到早</option>
              <option value="signupTime_desc">我的报名时间：最新报名</option>
              <option value="signupTime_asc">我的报名时间：最早报名</option>
              <option value="participants_desc">活动规模：人数由高到低</option>
            </select>
          </Field>
        </div>
      </section>
      <DataState request={request}>
        {!rows(request.data).length && (
          <Empty text="暂无符合条件的报名活动">
            <p>试试调整筛选条件，或者去认识身边的宠友。</p>
            <Link className="btn primary" to="/pets?type=2">
              探索同城活动
            </Link>
          </Empty>
        )}
        <div className="signup-list">
          {rows(request.data).map((item) => {
            const a = item.activity,
              s = item.signup;
            return (
              <article className="signup-card" key={s.signupId}>
                <div className="signup-activity">
                  <div className="signup-cover">
                    <RouteImage src={a.images || "/favicon-paw.svg"} loading="lazy" alt={a.title} />
                    <Badge>{item.available ? activityName(a.status) : "活动暂不可查看"}</Badge>
                  </div>
                  <div className="signup-description">
                    <h2>
                      {item.available ? (
                        <Link to={`/pets/activity/${a.id}`}>{a.title}</Link>
                      ) : (
                        a.title
                      )}
                    </h2>
                    <p className="signup-excerpt">{a.content}</p>
                    <p>
                      <Clock size={14} />
                      {time(a.startTime)} 至 {time(a.endTime)}
                    </p>
                    <p>
                      <MapPin size={14} />
                      {a.location}
                    </p>
                    <p>发起人：{nick(a.user)}</p>
                    <p>
                      <Users size={14} />
                      全团规模：{a.currentPeople || 0} / {a.maxPeople} 人
                    </p>
                    <progress
                      aria-label="活动报名进度"
                      value={a.currentPeople || 0}
                      max={Math.max(1, a.maxPeople || 1)}
                    />
                    {!item.available && (
                      <small>活动已删除、下架或正在审核，报名记录仍为您保留。</small>
                    )}
                  </div>
                </div>
                <aside className="signup-proof">
                  <div className="signup-proof-heading">
                    <strong>我的报名凭证</strong>
                    <Badge tone={s.status === 3 ? "" : "orange"}>
                      {s.status === 3 && <CheckCircle2 size={12} />} {signupName(s.status)}
                    </Badge>
                  </div>
                  <dl>
                    <div>
                      <dt>参与人员</dt>
                      <dd>{s.realName}</dd>
                    </div>
                    <div>
                      <dt>联系手机</dt>
                      <dd>{s.phone}</dd>
                    </div>
                    <div>
                      <dt>报名时间</dt>
                      <dd>{time(s.signupTime)}</dd>
                    </div>
                  </dl>
                  {s.remark && <p className="signup-note">携带 / 随行：{s.remark}</p>}
                  <div className="row-actions">
                    <Button action={() => setTicket(item)}>
                      <Ticket size={14} />
                      入场凭证
                    </Button>
                    {item.available && (
                      <Link className="btn" to={`/pets/activity/${a.id}`}>
                        查看活动详情 →
                      </Link>
                    )}
                    {item.canCancel && <Button action={() => setCancel(item)}>取消报名</Button>}
                  </div>
                  <small>现场签到由活动发起人核验</small>
                </aside>
              </article>
            );
          })}
        </div>
        <Pagination page={page} total={request.data?.total || 0} onChange={setPage} />
      </DataState>
      {ticket && (
        <Modal title="我的活动报名凭证" onClose={() => setTicket(null)}>
          <div className="signup-ticket">
            <Ticket size={42} />
            <h2>{ticket.activity.title}</h2>
            <p>凭证编号 #{ticket.signup.signupId}</p>
            <Badge>{signupName(ticket.signup.status)}</Badge>
            <dl>
              {[
                ["活动时间", time(ticket.activity.startTime)],
                ["举办地点", ticket.activity.location],
                ["参与人员", ticket.signup.realName],
                ["联系手机", ticket.signup.phone],
                ["报名时间", time(ticket.signup.signupTime)],
                ["签到时间", time(ticket.checkInTime)],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
            <p className="muted">请向活动发起人出示此报名信息，由发起人核验并完成签到。</p>
            <Button action={() => setTicket(null)}>关闭</Button>
          </div>
        </Modal>
      )}
      {cancel && (
        <Modal title="取消活动报名确认" onClose={() => setCancel(null)}>
          <p>确定取消《{cancel.activity.title}》吗？取消后席位将释放，如需参加需要重新报名。</p>
          <div className="row-actions">
            <Button action={() => setCancel(null)}>保留席位</Button>
            <Button
              tone="danger"
              action={async () => {
                await api(`/activity/cancelSignUp/${cancel.activity.id}`, "POST");
                setCancel(null);
                notify("已取消报名");
                request.reload();
                summary.reload();
                if (rows(request.data).length === 1 && page > 1) setPage(page - 1);
              }}
            >
              确认取消报名
            </Button>
          </div>
        </Modal>
      )}
    </div>
  );
}
