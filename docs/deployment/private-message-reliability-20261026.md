# 2026-10-26 私信可靠性发布

源版本：`0244f08`。发布目录：`/opt/pms/releases/private-reliable-0244f08-20261026`。

前后端于北京时间 17:20 发布完成，17:25 完成最终验证。公开访问地址仍为 http://111.229.254.252 。主 JAR SHA-256：`6b6632657920fc86b7b797c490f73f33e68e4d5c6894ef0c4620ed12adc6bc71`。

## 表结构与发布

执行前核对字段和等价唯一索引，再以 `ALGORITHM=INPLACE, LOCK=NONE` 添加：

- `private_message.client_message_id`：可空 `VARCHAR(64)`，`ascii / ascii_bin`。
- `uk_private_message_sender_client (from_user_id, client_message_id)`：唯一索引。

已有历史消息保持 NULL，不回填、不清空消息，也不改变原游标查询索引。先迁移，再更新核心服务；接口验证通过后发布 React 资源并原子替换入口。Gateway / Eureka / Config / 两个搜索实例与 XXL-JOB 配置保持原样，五份原应用配置逐项比对保持一致。

## 验证结果

服务端真实接口验证：

- 重复请求返回原消息 ID，数据库只落库一次，未读数只增加一次。
- 并发使用同一标识重试只得到一条消息。
- 同一标识换正文或接收者会被拒绝。
- 用排他 `afterId` 分批补齐遗漏消息，保留成员访问限制。

客户端实测使用项目实际 React Hook、JSDOM 渲染和公网 HTTP / WebSocket，未操作用户浏览器：

- 发送请求在服务器成功提交后，客户端主动丢弃该回包。确认输入内容保留、出现失败项，随后重试沿用原标识，返回原消息 ID；接收方未读仍为 1。
- 建立真实通知 WebSocket 后主动关闭；接收端处于模拟离线状态期间，另一个临时用户通过真实接口发送 2 条消息。恢复联网并建立第二次真实连接后，前端按确认游标补齐两条消息，没有重复。
- 数据库再次核对该临时会话只存在 3 条消息：1 条经历重试的消息，加 2 条断线期间消息；每个客户端标识仅有一条记录。

验证后注销并删除临时账户、会话和消息，删除临时令牌文件。六个后端进程正常，当前核心进程未发现 ERROR；公网 React 入口与本次构建一致，三个入口资源均可访问。

结果文件位于发布目录：`schema-verification.json`、`reliability-verification.json`、`live-client-verification.json`、`live-database-verification.json`、`verification-final.json`。这些文件不包含访问令牌。

## 回滚资料

发布目录保留 `backend-before.jar`、`index-before.html`、`current-before.json`，原版本为 `48fb55e` 后端和 `2b4ba58` 前端。发布脚本在切换失败时恢复原 JAR、入口及发布指针。已新增的可空字段与唯一索引兼容旧代码，代码回滚时可保留，不应删除消息或回填旧请求标识。
