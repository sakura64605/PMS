import { useEffect, useRef, useState } from "react";
import { useRouteLocation } from "./routeTransition";
import { marked } from "marked";
import DOMPurify from "dompurify";
import {
  ArrowLeft,
  Bot,
  MessageCircle,
  NotebookPen,
  Plus,
  Send,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  Users,
  PanelLeftClose,
  Sparkles,
  Copy,
} from "lucide-react";
import { api, copyText, rows, uid, nick, time, url, type Row } from "./api";
import {
  Avatar,
  Badge,
  Button,
  DataState,
  Empty,
  Field,
  Intro,
  Link,
  Modal,
  Pagination,
  SearchForm,
  navigate,
  useApp,
  useData,
} from "./core";

export function PrivateChat() {
  const route = useRouteLocation();
  const routeConversation = new URLSearchParams(route.split('?')[1]).get('conversation');
  const { user, notify, refresh } = useApp();
  const [page, setPage] = useState(1),
    [selected, setSelected] = useState<Row | null>(null),
    [messagePage, setMessagePage] = useState(1),
    [text, setText] = useState(""),
    [find, setFind] = useState(false),
    [keyword, setKeyword] = useState(""),
    [showList, setShowList] = useState(true);
  const list = useData(
    url("/message/private/conversations", { pageNum: page, pageSize: 20 }),
  );
  const history = useData(
    selected || routeConversation
      ? url("/message/private/messages", {
          conversationId: selected?.conversationId || routeConversation,
          pageNum: messagePage,
          pageSize: 20,
        })
      : null,
  );
  const search = useData(keyword ? url("/user/search", { keyword }) : null);
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (messagePage === 1) end.current?.scrollIntoView({ block: "nearest" });
  }, [history.data, messagePage]);
  useEffect(() => {
    const id = new URLSearchParams(route.split('?')[1]).get("conversation");
    if (id) {
      const found = rows(list.data).find(
        (c) => String(c.conversationId) === id,
      );
      if (found) {
        setSelected(found);
        setShowList(false);
      }
    }
  }, [list.data, route]);
  async function choose(c: Row) {
    setSelected(c);
    setMessagePage(1);
    setShowList(false);
    await api(`/message/private/read/${c.conversationId}`, "PUT");
    list.reload();
    refresh();
  }
  async function send() {
    if (!text.trim() || !selected) return;
    await api("/message/private/send", "POST", {
      toUserId: selected.otherUser?.userId || selected.otherUser?.id,
      content: text.trim(),
      messageType: 1,
    });
    setText("");
    setMessagePage(1);
    history.reload();
    list.reload();
    refresh();
  }
  return (
    <>
      <Intro title="私信" description="和聊得来的宠友，多分享一点生活。">
        <Button tone="primary" action={() => setFind(true)}>
          <Plus size={14} />
          新私信
        </Button>
      </Intro>
      <div className="chat-layout">
        <aside className={`chat-sidebar ${!showList ? "hidden-mobile" : ""}`}>
          <h2>我的会话</h2>
          <DataState request={list} empty={!rows(list.data).length}>
            {rows(list.data).map((c) => (
              <button
                className={`chat-session ${selected?.conversationId === c.conversationId ? "active" : ""}`}
                key={c.conversationId}
                onClick={() => {
                  void choose(c).catch((e) => notify(e.message, true));
                }}
              >
                <Avatar user={c.otherUser} />
                <span style={{ flex: 1, minWidth: 0 }}>
                  <strong>{nick(c.otherUser)}</strong>
                  <small>{c.lastMessage || "开始聊聊吧"}</small>
                  <small>{time(c.lastMessageTime)}</small>
                </span>
                {c.unreadCount > 0 && (
                  <Badge tone="orange">{c.unreadCount}</Badge>
                )}
              </button>
            ))}
          </DataState>
          <Pagination
            page={page}
            size={20}
            total={list.data?.total || 0}
            onChange={setPage}
          />
        </aside>
        <section className="chat-main">
          {selected ? (
            <>
              <header className="chat-head">
                <div className="author">
                  <Button
                    tone="ghost small"
                    action={() => setShowList((v) => !v)}
                    aria-label="切换会话列表"
                  >
                    <PanelLeftClose size={17} />
                  </Button>
                  <Avatar user={selected.otherUser} size={32} />
                  <h2>{nick(selected.otherUser)}</h2>
                </div>
                <div className="row-actions">
                  <Button tone="ghost small" action={() => choose(selected)}>
                    标记已读
                  </Button>
                  <Button
                    tone="ghost small"
                    action={async () => {
                      if (confirm("确定清空聊天记录吗？")) {
                        await api(
                          `/message/private/messages/${selected.conversationId}`,
                          "DELETE",
                        );
                        history.reload();
                        list.reload();
                      }
                    }}
                  >
                    清空
                  </Button>
                  <Button
                    tone="danger small"
                    action={async () => {
                      if (confirm("确定删除会话吗？")) {
                        await api(
                          `/message/private/conversation/${selected.conversationId}`,
                          "DELETE",
                        );
                        setSelected(null);
                        list.reload();
                        setShowList(true);
                      }
                    }}
                  >
                    删除会话
                  </Button>
                </div>
              </header>
              <div className="chat-messages">
                <DataState request={history} empty={!rows(history.data).length}>
                  {[...rows(history.data)]
                    .sort((a, b) =>
                      String(a.createTime).localeCompare(String(b.createTime)),
                    )
                    .map((m) => (
                      <div
                        className={`bubble-wrap ${String(m.fromUserId) === uid(user) ? "me" : ""}`}
                        key={m.id}
                      >
                        <div className="bubble">{m.content}</div>
                        <small>{time(m.createTime)}</small>
                      </div>
                    ))}
                </DataState>
                <Pagination
                  page={messagePage}
                  size={20}
                  total={history.data?.total || 0}
                  onChange={setMessagePage}
                />
                <div ref={end} />
              </div>
              <form
                className="chat-compose"
                onSubmit={(e) => e.preventDefault()}
              >
                <textarea
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  maxLength={5000}
                  aria-label="私信内容"
                  placeholder="分享一点今天的小事…"
                />
                <Button tone="primary" disabled={!text.trim()} action={send}>
                  <Send size={16} />
                  发送
                </Button>
              </form>
            </>
          ) : (
            <div className="chat-empty">
              <div>
                <MessageCircle size={40} />
                <h2>从一句你好开始</h2>
                <p>选择会话，或找一位宠友聊聊。</p>
                <Button action={() => setFind(true)}>寻找宠友</Button>
              </div>
            </div>
          )}
        </section>
      </div>
      {find && (
        <Modal title="发起新私信" onClose={() => setFind(false)}>
          <SearchForm onSearch={setKeyword} placeholder="搜索用户名或昵称" />
          <DataState request={search}>
            {rows(search.data).map((u) => (
              <div className="notice-row" key={uid(u)}>
                <div className="author">
                  <Avatar user={u} />
                  <span>
                    {nick(u)}
                    <small> @{u.username}</small>
                  </span>
                </div>
                <Button
                  disabled={uid(u) === uid(user)}
                  action={async () => {
                    const c = await api(
                      url("/message/private/conversation", {
                        otherUserId: uid(u),
                      }),
                    );
                    await choose(c);
                    setFind(false);
                    list.reload();
                  }}
                >
                  聊天
                </Button>
              </div>
            ))}
          </DataState>
        </Modal>
      )}
    </>
  );
}
function Memory({ onClose }: { onClose: () => void }) {
  const request = useData("/ai/memory");
  const [text, setText] = useState(""),
    [editing, setEditing] = useState<string | null>(null),
    [editText, setEditText] = useState("");
  return (
    <Modal title="我的长期记忆" onClose={onClose}>
      <p className="muted">
        AI 会记住你分享的重要信息，你也可以在这里查看、修改或删除。
      </p>
      <Field label="添加记忆">
        <input
          value={text}
          maxLength={200}
          onChange={(e) => setText(e.target.value)}
          placeholder="如：我养了一只橘猫叫圆圆"
        />
      </Field>
      <Button
        tone="primary"
        disabled={!text.trim()}
        action={async () => {
          await api("/ai/memory", "POST", { content: text.trim() });
          setText("");
          request.reload();
        }}
      >
        添加
      </Button>
      <DataState request={request} empty={!rows(request.data).length}>
        {rows(request.data).map((m) => (
          <div className="memory-row" key={m.id}>
            {editing === String(m.id) ? (
              <>
                <textarea
                  value={editText}
                  maxLength={200}
                  onChange={(e) => setEditText(e.target.value)}
                />
                <div className="row-actions">
                  <Button
                    action={async () => {
                      if (!editText.trim()) throw new Error("记忆内容不能为空");
                      await api(`/ai/memory/${m.id}`, "PUT", {
                        content: editText.trim(),
                      });
                      setEditing(null);
                      request.reload();
                    }}
                  >
                    保存
                  </Button>
                  <Button action={() => setEditing(null)}>取消</Button>
                </div>
              </>
            ) : (
              <>
                <p>{m.content}</p>
                <small>{time(m.updatedAt || m.createdAt)}</small>
                <div className="row-actions">
                  <Button
                    tone="ghost small"
                    action={() => {
                      setEditing(String(m.id));
                      setEditText(m.content);
                    }}
                  >
                    编辑
                  </Button>
                  <Button
                    tone="danger small"
                    action={async () => {
                      if (confirm("删除这条记忆吗？")) {
                        await api(`/ai/memory/${m.id}`, "DELETE");
                        request.reload();
                      }
                    }}
                  >
                    删除
                  </Button>
                </div>
              </>
            )}
          </div>
        ))}
      </DataState>
    </Modal>
  );
}
export function AIChat() {
  const [session, setSession] = useState(""),
    [text, setText] = useState(""),
    [sending, setSending] = useState(false),
    [optimistic, setOptimistic] = useState<Row[]>([]),
    [memory, setMemory] = useState(false),
    [showList, setShowList] = useState(true),
    [lastError, setLastError] = useState("");
  const sessions = useData("/ai/sessions"),
    history = useData(session ? `/ai/history/${session}` : null),
    suggestions = useData("/ai/suggestions");
  const { notify } = useApp();
  const end = useRef<HTMLDivElement>(null);
  const displayed = [...rows(history.data), ...optimistic];
  useEffect(() => {
    end.current?.scrollIntoView({ block: "nearest" });
  }, [displayed.length, sending]);
  async function send() {
    if (sending || !text.trim()) return;
    const message = text.trim();
    setSending(true);
    setLastError("");
    setOptimistic([
      { role: "user", content: message, createdAt: new Date().toISOString() },
    ]);
    try {
      const result = await api("/ai/chat", "POST", {
        sessionId: session || undefined,
        message,
      });
      setText("");
      if (result.sessionId) setSession(result.sessionId);
      setOptimistic([]);
      history.reload();
      sessions.reload();
    } catch (e: any) {
      setOptimistic([]);
      setLastError(e.message);
      throw e;
    } finally {
      setSending(false);
    }
  }
  return (
    <div className="ai-workspace">
      <div className={`chat-layout ${!showList ? "sidebar-collapsed" : ""}`}>
        <aside className={`chat-sidebar ${!showList ? "hidden-mobile" : ""}`}>
          <div className="ai-sidebar-heading">
            <span className="ai-eyebrow">PETCIRCLE ASSISTANT</span>
            <Button tone="ghost small ai-mobile-close" aria-label="收起历史会话" action={() => setShowList(false)}><PanelLeftClose size={17}/></Button>
            <Button
              tone="primary ai-new-chat"
              disabled={sending}
              action={() => {
                setSession("");
                setOptimistic([]);
              }}
            >
              <Plus size={16} />开启新咨询
            </Button>
            <h2>历史会话</h2>
          </div>
          <DataState request={sessions}>
            {rows(sessions.data).map((s) => (
              <div
                className={`chat-session ${session === s.sessionId ? "active" : ""}`}
                key={s.sessionId}
              >
                <button
                  disabled={sending}
                  style={{
                    background: "none",
                    border: 0,
                    textAlign: "left",
                    flex: 1,
                    minWidth: 0,
                  }}
                  onClick={() => {
                    setSession(s.sessionId);
                    setOptimistic([]);
                    setShowList(false);
                  }}
                >
                  <strong>{s.title || s.sessionName || "养宠对话"}</strong>
                  <small>{time(s.updatedAt || s.createdAt)}</small>
                </button>
                <Button
                  tone="ghost small"
                  disabled={sending}
                  aria-label="删除会话"
                  action={async () => {
                    if (confirm("删除这个对话？")) {
                      await api(`/ai/session/${s.sessionId}`, "DELETE");
                      if (session === s.sessionId) setSession("");
                      sessions.reload();
                    }
                  }}
                >
                  <Trash2 size={14} />
                </Button>
              </div>
            ))}
          </DataState>
          <div className="ai-sidebar-note"><Sparkles size={18}/><span>用心倾听，温暖陪伴<small>记录你和毛孩子的每一次交流</small></span></div>
        </aside>
        <section className="chat-main">
          <header className="chat-head">
            <div className="author">
              <Button
                tone="ghost small"
                aria-label="显示或隐藏会话"
                action={() => setShowList((v) => !v)}
              >
                <PanelLeftClose size={17} />
              </Button>
              <span className="ai-brand-icon"><Bot size={19} /></span>
              <div>
                <h2>PetCircle AI 养宠顾问 <span className="ai-service-badge">AI 助手</span></h2>
                <small>{sending ? "正在认真思考…" : "营养、日常护理与行为习惯，陪你一起了解毛孩子"}</small>
              </div>
            </div>
            <div className="row-actions ai-tools" aria-label="AI 会话工具">
              <Button tone="ghost small" action={() => setMemory(true)}>
                <NotebookPen size={14} />
                长期记忆
              </Button>
              <Button
                tone="ghost small"
                disabled={!session || sending}
                action={async () => {
                  if (confirm("清除本会话记忆？历史记录仍会保留。")) {
                    await api(`/ai/memory/${session}`, "DELETE");
                    notify("会话记忆已清除");
                  }
                }}
              >
                清除记忆
              </Button>
              <Button
                tone="small"
                disabled={!session}
                action={async () => {
                  await api(
                    url("/ai/transfer", {
                      sessionId: session,
                      reason: "用户申请人工服务",
                    }),
                    "POST",
                  );
                  notify("转人工申请已提交");
                }}
              >
                转人工
              </Button>
            </div>
          </header>
          <div className="chat-messages">
            {!session && !displayed.length && !sending ? (
              <div className="chat-empty">
                <div>
                  <span className="ai-welcome-icon"><Sparkles size={34} /></span>
                  <span className="ai-eyebrow">YOUR PET COMPANION</span><h2>你好呀，我是你的养宠小伙伴</h2>
                  <p>从一日三餐到生活里的小习惯，<br/>一起聊聊你和毛孩子的故事。</p>
                  <div className="suggestions">
                    {(suggestions.data || []).map((s: string) => (
                      <Button key={s} tone="small" action={() => setText(s)}>
                        <MessageCircle size={15} />{s}
                      </Button>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <DataState request={history}>
                {displayed.map((m, i) => (
                  <div
                    className={`bubble-wrap ${m.role === "user" ? "me" : ""}`}
                    key={m.id || i}
                  >
                    <span className="ai-message-avatar" aria-hidden="true">{m.role === "user" ? "我" : "AI"}</span>
                    <div className="ai-message-body"><div
                      className="bubble markdown"
                      dangerouslySetInnerHTML={{
                        __html: DOMPurify.sanitize(
                          marked.parse(String(m.content || ""), {
                            async: false,
                          }) as string,
                        ),
                      }}
                    />
                    <small>{time(m.createdAt)}</small>
                    {m.role !== "user" && m.id && (
                      <div className="row-actions ai-message-actions">
                        <Button tone="ghost small" aria-label="复制回答" action={async () => {await copyText(String(m.content || ''));notify('回答已复制');}}><Copy size={13}/></Button>
                        <Button
                          tone="ghost small"
                          aria-label="有帮助"
                          action={async () => {
                            await api(
                              url("/ai/feedback", {
                                messageId: m.messageId || m.id,
                                score: 1,
                              }),
                              "POST",
                            );
                            notify("感谢反馈");
                          }}
                        >
                          <ThumbsUp size={13} />
                        </Button>
                        <Button
                          tone="ghost small"
                          aria-label="无帮助"
                          action={async () => {
                            await api(
                              url("/ai/feedback", {
                                messageId: m.messageId || m.id,
                                score: 0,
                              }),
                              "POST",
                            );
                            notify("感谢反馈");
                          }}
                        >
                          <ThumbsDown size={13} />
                        </Button>
                      </div>
                    )}
                    </div>
                  </div>
                ))}
              </DataState>
            )}
            {sending && (
              <div className="bubble-wrap"><span className="ai-message-avatar" aria-hidden="true">AI</span><div className="bubble ai-thinking" role="status"><span/><span/><span/><small>正在整理思路…</small></div></div>
            )}
            {lastError && (
              <p className="inline-error" role="alert">
                {lastError}，输入内容已保留，可重新发送。
              </p>
            )}
            <div ref={end} />
          </div>
          <form className="chat-compose" onSubmit={(e) => e.preventDefault()}>
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              maxLength={10000}
              disabled={sending}
              placeholder="想了解毛孩子的什么呢？"
              aria-label="给 AI 的问题"
              onKeyDown={e => {if(e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing){e.preventDefault();if(!sending && text.trim()) void send().catch(error => notify(error.message,true));}}}
            />
            <Button
              tone="primary"
              disabled={sending || !text.trim()}
              action={send}
            >
              <Send size={16} />
              发送
            </Button>
          </form>
          <div className="ai-compose-hint">Enter 发送 · Shift+Enter 换行</div>
        </section>
      </div>
      {memory && <Memory onClose={() => setMemory(false)} />}
    </div>
  );
}
