import { PetRecords } from "./petRecords";
import { useState } from "react";
import { api, rows, type Row } from "./api";
import { Button, Modal, useApp, useData, DataState } from "./core";
const blank = { name: "", species: "cat", breed: "", birthDate: "", sex: "unknown" };
export function PetProfiles({ onClose, onChange }: { onClose: () => void; onChange: () => void }) {
  const [recordPet, setRecordPet] = useState<Row | null>(null);
  const pets = useData("/ai/pets");
  const { notify } = useApp();
  const [editing, setEditing] = useState<Row | null>(null);
  const [form, setForm] = useState<Row>({ ...blank });
  const [busy, setBusy] = useState(false);
  const field = (key: string, value: string) => setForm((old) => ({ ...old, [key]: value }));
  async function save() {
    setBusy(true);
    try {
      await api(editing ? `/ai/pets/${editing.id}` : "/ai/pets", editing ? "PUT" : "POST", {
        ...form,
        birthDate: form.birthDate || null,
      });
      pets.reload();
      onChange();
      setEditing(null);
      setForm({ ...blank });
      notify("宠物档案已保存");
    } finally {
      setBusy(false);
    }
  }
  if (recordPet) return <PetRecords pet={recordPet} onClose={() => setRecordPet(null)} />;
  return (
    <Modal title="我的宠物档案" onClose={onClose}>
      <p>由你维护的信息会用于该宠物的咨询。未知的品种、出生日期可以留空；聊天不会自动修改档案。</p>
      <DataState request={pets}>
        {rows(pets.data).map((pet) => (
          <div className="ai-pet-row" key={pet.id}>
            <span>
              <strong>{pet.name}</strong> ·{" "}
              {pet.species === "cat" ? "猫" : pet.species === "dog" ? "狗" : "其他"} {pet.breed}
            </span>
            <Button tone="ghost small" disabled={busy} action={() => setRecordPet(pet)}>
              档案记录
            </Button>
            <Button
              tone="ghost small"
              disabled={busy}
              action={() => {
                setEditing(pet);
                setForm({
                  name: pet.name,
                  species: pet.species,
                  breed: pet.breed || "",
                  birthDate: pet.birthDate || "",
                  sex: pet.sex,
                });
              }}
            >
              编辑
            </Button>
            <Button
              tone="ghost small"
              disabled={busy}
              action={async () => {
                if (!confirm(`删除 ${pet.name} 的档案？原会话记录会保留，但不能继续以此档案咨询。`))
                  return;
                setBusy(true);
                try {
                  await api(`/ai/pets/${pet.id}`, "DELETE");
                  pets.reload();
                  onChange();
                  if (editing?.id === pet.id) {
                    setEditing(null);
                    setForm({ ...blank });
                  }
                } finally {
                  setBusy(false);
                }
              }}
            >
              删除
            </Button>
          </div>
        ))}
      </DataState>
      <form
        className="ai-pet-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save().catch((e) => notify(e.message, true));
        }}
      >
        <h3>{editing ? "编辑档案" : "添加宠物"}</h3>
        <label>
          名字
          <input
            required
            maxLength={40}
            value={form.name}
            onChange={(e) => field("name", e.target.value)}
          />
        </label>
        <label>
          物种
          <select value={form.species} onChange={(e) => field("species", e.target.value)}>
            <option value="cat">猫</option>
            <option value="dog">狗</option>
            <option value="other">其他</option>
          </select>
        </label>
        <label>
          品种（选填）
          <input
            maxLength={60}
            value={form.breed}
            onChange={(e) => field("breed", e.target.value)}
          />
        </label>
        <label>
          出生日期（不知道可留空）
          <input
            type="date"
            value={form.birthDate}
            onChange={(e) => field("birthDate", e.target.value)}
          />
        </label>
        <label>
          性别
          <select value={form.sex} onChange={(e) => field("sex", e.target.value)}>
            <option value="unknown">未知</option>
            <option value="male">公</option>
            <option value="female">母</option>
          </select>
        </label>
        <div className="row-actions">
          <button className="btn primary" type="submit" disabled={busy || !form.name.trim()}>
            {busy ? "保存中…" : "保存档案"}
          </button>
          {editing && (
            <Button
              tone="ghost"
              disabled={busy}
              action={() => {
                setEditing(null);
                setForm({ ...blank });
              }}
            >
              取消编辑
            </Button>
          )}
        </div>
      </form>
    </Modal>
  );
}
