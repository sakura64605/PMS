# 私信历史游标与上滑加载

接口路径仍为 `GET /pet-system/message/private/messages`。

- 首次请求：`conversationId=...&pageSize=30`，取最近消息。
- 更早历史：传上批返回的 `nextCursor` 为 `beforeId`，只查询 `id < beforeId`。
- 新消息增量：`afterId` 查询 `id > afterId`；不能与 `beforeId` 同传。
- `pageSize` 限制 1–100，默认 30。返回 `records`、`nextCursor`、`hasMore`，不再计算总数或 OFFSET。

历史响应按 ID 降序，`nextCursor` 就是最后一条记录的 ID；新消息增量按 ID 升序。SQL 多取一条判断 `hasMore`。边界排他，游标消息被删除后仍可继续；可见性筛选和会话成员校验保留。原分页 DTO / Java 方法保留兼容内部旧调用，公开聊天历史入口已改为游标。

聊天窗口初始定位到底部，接近顶部时自动加载历史，合并去重后补偿 scrollTop。发送消息直接追加，推送通知 / 可见页面定时检查用独立的已同步 ID 获取增量，避免发送回包导致遗漏中间消息。阅读旧消息时收到新消息不强制滚到底部；主动发送则定位新消息。切换联系人、清空历史和卸载会废弃旧请求。

索引用于 `(conversation_id, id)` 范围查询；新环境部署前可执行 `database/manual/20261026-private-message-cursor-index.sql`，先检查有无等价索引。2026-10-26 已按用户授权添加线上索引并同步发布前后端，详见[发布记录](../deployment/private-message-cursor-20261026.md)。

## 消息时间展示

第一条消息、与前一条间隔至少 5 分钟、或跨天时，在消息组之间独立居中显示时间，分隔不属于任何气泡。今天只显示时分，昨天显示“昨天”，其他日期显示月日，跨年显示年份。每条气泡用原生悬停提示显示精确到秒的时间，不常驻显示时间。按北京时间解释后端无时区的 LocalDateTime；上滑合并历史后重新计算相邻分隔，避免分页边界产生重复标签。本展示改动尚未部署。
