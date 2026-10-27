import type { Row } from "./api";

// Existing messages expose businessId/link; targetId is optional in older records.
export function notificationTarget(message: Row): string | null {
  if (
    typeof message.link === "string" &&
    message.link.startsWith("/") &&
    !message.link.startsWith("//") &&
    !message.link.includes("\\")
  ) {
    const link = message.link
      .replace(/^\/pet\//, "/pets/")
      .replace(/^\/activity\//, "/pets/activity/");
    if (/^\/(?:pets|daily|user|notice)(?:\/|\?|#|$)/.test(link)) return link;
  }
  const id = message.targetId ?? message.businessId;
  if (!/^\d+$/.test(String(id)) || Number(id) <= 0) return null;
  const target = message.targetType;
  if (["activity", "pet_activity"].includes(target)) return `/pets/activity/${id}`;
  if (["pet", "pet_post", "adopt", "help"].includes(target)) return `/pets/${id}`;
  if (["daily", "daily_post"].includes(target)) return `/daily/${id}`;
  if (["notice", "announcement"].includes(target) || message.type === "NOTICE")
    return `/notice/${id}`;
  if (target === "user") return `/user/${id}`;
  return null;
}
