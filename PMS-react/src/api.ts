export type Row = Record<string, any>;
export function url(path: string, params: Row = {}) {
  const q = new URLSearchParams();
  for (const [key, value] of Object.entries(params))
    if (value !== "" && value != null) {
      if (Array.isArray(value)) value.forEach((v) => q.append(key, String(v)));
      else q.set(key, String(value));
    }
  return path + (q.size ? (path.includes("?") ? "&" : "?") + q.toString() : "");
}
export async function api<T = any>(
  path: string,
  method = "GET",
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const token = localStorage.getItem("token");
  const headers: Record<string, string> = token
    ? { Authorization: `Bearer ${token.replace(/^Bearer\s+/i, "")}` }
    : {};
  if (body != null && !(body instanceof FormData))
    headers["Content-Type"] = "application/json";
  const timeout = AbortSignal.timeout(path === "/ai/chat" ? 120000 : 20000);
  const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
  const response = await fetch("/pet-system" + path, {
    method,
    headers,
    signal: requestSignal,
    body:
      body == null
        ? undefined
        : body instanceof FormData
          ? body
          : JSON.stringify(body),
  });
  const result = await response
    .json()
    .catch(() => ({ message: "服务返回格式异常，请稍后重试" }));
  if (response.status === 401 || result.code === 401) {
    localStorage.removeItem("token");
    localStorage.removeItem("userInfo");
    window.dispatchEvent(new Event("auth-cleared"));
  }
  if (!response.ok || result.code !== 200)
    throw new Error(
      result.message || result.msg || `请求失败（${response.status}）`,
    );
  return result.data;
}
export const rows = (data: any): Row[] =>
  Array.isArray(data) ? data : data?.records || data?.list || [];
export const uid = (user?: Row | null) =>
  String(user?.userId ?? user?.id ?? "");
export const nick = (user?: Row | null) =>
  user?.nickName ||
  user?.nickname ||
  user?.userName ||
  user?.username ||
  "宠友";
export const time = (value: any) =>
  value ? String(value).replace("T", " ").slice(0, 16) : "—";
export const images = (value: any): string[] =>
  Array.isArray(value)
    ? value.filter(
        (v): v is string =>
          typeof v === "string" && /^(https?:\/\/|\/)/i.test(v),
      )
    : [];
export const canEdit = (row: Row) =>
  row.status !== 2 && row.status !== -1 && !row.deleted;
export const typeName = (type: string) =>
  ({
    adopt: "领养",
    help: "救助",
    activity: "活动",
    daily: "日记",
    pet: "宠物",
    comment: "评论",
    user: "用户",
  })[type] || type;
export const auditName = (code: number) =>
  ["待审核", "已通过", "已拒绝"][code] || "未知";
export const petName = (code: number) =>
  ({
    "-1": "已删除",
    "0": "待审核",
    "1": "已发布",
    "2": "已完成",
    "3": "已下架",
    "4": "已拒绝",
    "5": "已下架",
  })[String(code)] || "未知";
export const activityName = (code: number) =>
  ["报名中", "进行中", "已结束", "已取消"][code] || "未知";

/** Also works on the existing HTTP deployment, where Clipboard API is unavailable. */
export async function copyText(text: string) {
  if (navigator.clipboard?.writeText) { await navigator.clipboard.writeText(text); return; }
  const input = document.createElement('textarea');
  input.value = text; input.style.position = 'fixed'; input.style.opacity = '0';
  document.body.appendChild(input); input.select();
  try { if (!document.execCommand('copy')) throw new Error('无法自动复制，请手动选择内容复制'); }
  finally { input.remove(); }
}
