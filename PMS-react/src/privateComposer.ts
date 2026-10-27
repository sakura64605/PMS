import { useEffect, useRef, useState } from "react";
import { api, type Row } from "./api";

type Delivery = {
  clientMessageId: string;
  content: string;
  toUserId: string;
  messageType: number;
  status: "sending" | "failed";
  error: string;
  delivered: boolean;
  submittedDraft?: string;
  submittedRevision?: number;
};
type Draft = { text: string; revision: number; jobs: Delivery[] };

// crypto.randomUUID is unavailable on the existing plain HTTP site.
function messageKey() {
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map((value) => value.toString(16).padStart(2, "0")).join("");
}

export function usePrivateComposer(
  actor: string,
  conversation: unknown,
  recipient: unknown,
  onDelivered: (message: Row, conversationId: string, fromHistory: boolean) => void,
) {
  const stores = useRef(new Map<string, Draft>());
  const owner = useRef(actor);
  owner.current = actor;
  const delivered = useRef(onDelivered);
  delivered.current = onDelivered;
  const alive = useRef(false);
  const [, render] = useState(0);
  const id = conversation == null ? "" : String(conversation);
  const key = `${actor}:${id}`;
  const store = stores.current.get(key) || { text: "", revision: 0, jobs: [] };
  if (!stores.current.has(key)) stores.current.set(key, store);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    for (const existing of stores.current.keys())
      if (!existing.startsWith(`${actor}:`)) stores.current.delete(existing);
  }, [actor]);
  const publish = () => {
    if (alive.current && owner.current === actor) render((n) => n + 1);
  };

  const complete = (job: Delivery, message: Row, fromHistory = false) => {
    if (job.delivered) return;
    job.delivered = true;
    store.jobs = store.jobs.filter((value) => value !== job);
    if (store.revision === job.submittedRevision && store.text === job.submittedDraft)
      store.text = "";
    if (alive.current && owner.current === actor) delivered.current(message, id, fromHistory);
    publish();
  };
  const transmit = async (job: Delivery) => {
    if (
      job.delivered ||
      !alive.current ||
      store.jobs.some((value) => value.status === "sending") ||
      owner.current !== actor
    )
      return;
    job.status = "sending";
    job.error = "";
    if (!store.jobs.includes(job)) store.jobs.push(job);
    if (store.text.trim() === job.content) {
      job.submittedDraft = store.text;
      job.submittedRevision = store.revision;
    }
    publish();
    try {
      const message = await api<Row>("/message/private/send", "POST", {
        toUserId: job.toUserId,
        content: job.content,
        messageType: job.messageType,
        clientMessageId: job.clientMessageId,
      });
      if (!message || message.id == null) throw new Error("消息回执无效，请重试确认");
      complete(job, message);
    } catch (error) {
      if (!job.delivered) {
        job.status = "failed";
        job.error = (error as Error).message;
      }
      publish();
    }
  };
  const send = async () => {
    if (
      !id ||
      !recipient ||
      !store.text.trim() ||
      store.jobs.some((job) => job.status === "sending")
    )
      return;
    const previous = store.jobs.find((job) => !job.delivered && job.content === store.text.trim());
    await transmit(
      previous || {
        clientMessageId: messageKey(),
        content: store.text.trim(),
        toUserId: String(recipient),
        messageType: 1,
        status: "failed",
        error: "",
        delivered: false,
      },
    );
  };
  const reconcile = (messages: Row[]) => {
    for (const message of messages) {
      if (String(message.fromUserId) !== actor || !message.clientMessageId) continue;
      const job = store.jobs.find(
        (value) =>
          value.clientMessageId === message.clientMessageId && value.content === message.content,
      );
      if (job && message.id != null) complete(job, message, true);
    }
  };
  return {
    draft: store.text,
    setDraft: (text: string) => {
      store.text = text;
      store.revision++;
      publish();
    },
    send,
    retry: transmit,
    reconcile,
    pending: store.jobs,
    sending: store.jobs.some((job) => job.status === "sending"),
  };
}
