import { useState } from "react";
import { api, rows, time, type Row } from "./api";
import { Button, Modal, DataState, useData, useApp } from "./core";
const labels: Record<string, string> = { habit: "习惯", note: "注意事项", event: "历史事件" };
export function PetRecordEditor({
  petId,
  petName,
  record,
  onClose,
  onSaved,
}: {
  petId: string;
  petName: string;
  record?: Row;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<Row>({
    kind: record?.kind || "event",
    content: record?.content || "",
    occurredOn: record?.occurredOn || "",
    eventState: record?.eventState || "unknown",
    sourceMessageId: record?.sourceMessageId,
  });
  const [busy, setBusy] = useState(false);
  const { notify } = useApp();
  const field = (key: string, value: string) => setForm((old) => ({ ...old, [key]: value }));
  async function save() {
    setBusy(true);
    try {
      await api(
        `/ai/pets/${petId}/records${record?.id ? `/${record.id}` : ""}`,
        record?.id ? "PUT" : "POST",
        {
          ...form,
          occurredOn: form.kind === "event" ? form.occurredOn || null : null,
          eventState: form.kind === "event" ? form.eventState : null,
        },
      );
      notify("已确认保存到宠物档案");
      onSaved();
      onClose();
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title={`保存到 ${petName} 的档案`} onClose={onClose}>
      <p>
        请确认这是该宠物的真实情况。只保存你的陈述，不把助手建议、猜测或其他宠物的信息当作事实。
      </p>
      <form
        className="ai-pet-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save().catch((e) => notify(e.message, true));
        }}
      >
        <label>
          记录类型
          <select value={form.kind} onChange={(e) => field("kind", e.target.value)}>
            {Object.entries(labels).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          确认的内容
          <textarea
            required
            maxLength={1000}
            rows={5}
            value={form.content}
            onChange={(e) => field("content", e.target.value)}
          />
        </label>
        {form.kind === "event" && (
          <>
            <label>
              发生日期（不知道可留空）
              <input
                type="date"
                value={form.occurredOn}
                onChange={(e) => field("occurredOn", e.target.value)}
              />
            </label>
            <label>
              后续状态
              <select value={form.eventState} onChange={(e) => field("eventState", e.target.value)}>
                <option value="unknown">尚未确认</option>
                <option value="ongoing">记录时仍在持续</option>
                <option value="resolved">用户反馈已结束</option>
              </select>
            </label>
          </>
        )}
        {form.sourceMessageId && (
          <small>来源：你在当前宠物会话中发送的消息；内容可在保存前整理。</small>
        )}
        <button type="submit" className="btn primary" disabled={busy || !form.content.trim()}>
          {busy ? "保存中…" : "确认保存"}
        </button>
      </form>
    </Modal>
  );
}
export function PetRecords({ pet, onClose }: { pet: Row; onClose: () => void }) {
  const [before, setBefore] = useState<number | null>(null);
  const request = useData(`/ai/pets/${pet.id}/records${before ? `?beforeId=${before}` : ""}`);
  const [editing, setEditing] = useState<Row | null>(null);
  if (editing)
    return (
      <PetRecordEditor
        petId={String(pet.id)}
        petName={pet.name}
        record={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setBefore(null);
          request.reload();
        }}
      />
    );
  return (
    <Modal title={`${pet.name} 的档案记录`} onClose={onClose}>
      <p>这里的记录会用于新会话。历史经历不代表当前情况，记录中的健康信息是用户陈述。</p>
      <Button action={() => setEditing({})}>添加记录</Button>
      <DataState request={request} empty={!rows(request.data).length}>
        {rows(request.data).map((record) => (
          <article className="ai-pet-record" key={record.id}>
            <strong>
              {labels[record.kind]}
              {record.kind === "event" ? ` · ${record.occurredOn || "发生日期未知"}` : ""}
            </strong>
            <p style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{record.content}</p>
            <small>
              {record.kind === "event"
                ? { unknown: "状态未确认", ongoing: "记录时持续中", resolved: "用户反馈已结束" }[
                    record.eventState as string
                  ] || ""
                : ""}{" "}
              · 更新于 {time(record.updatedAt)} ·{" "}
              {record.sourceMessageId ? "从用户消息确认" : "手动维护"}
            </small>
            <div className="row-actions">
              <Button tone="ghost small" action={() => setEditing(record)}>
                纠正 / 补充后续
              </Button>
              <Button
                tone="ghost small"
                action={async () => {
                  if (confirm("删除此档案记录？删除后不再用于新会话。原聊天消息仍保留。")) {
                    await api(`/ai/pets/${pet.id}/records/${record.id}`, "DELETE");
                    request.reload();
                  }
                }}
              >
                删除
              </Button>
            </div>
          </article>
        ))}
      </DataState>
      <div className="row-actions">
        {before && (
          <Button tone="ghost small" action={() => setBefore(null)}>
            返回最新
          </Button>
        )}
        {rows(request.data).length === 50 && (
          <Button tone="ghost small" action={() => setBefore(rows(request.data).at(-1)!.id)}>
            更早的记录
          </Button>
        )}
      </div>
    </Modal>
  );
}
