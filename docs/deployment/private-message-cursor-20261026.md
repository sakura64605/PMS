# 2026-10-26 私信游标发布

源版本：`48fb55e`。发布目录：`/opt/pms/releases/private-cursor-48fb55e-20261026`。

本次只更新核心应用与 React 前端，沿用已运行的 Gateway / Eureka / Config / 双搜索实例，不改 Cloud 和 XXL-JOB 凭据。旧应用配置在组装主 JAR 时逐项保留，前端仍使用 `/var/www/html/pms`，兼容 Jenkins。

私信表新增 `idx_private_message_conversation_id (conversation_id, id)`，执行前检查已有索引是否等价，使用 MySQL `ALGORITHM=INPLACE, LOCK=NONE` 添加。此索引向后兼容；如果代码回滚，无需删除。

发布前保留 `backend-before.jar`、`index-before.html` 和 `current-before.json`。先检查索引，再更换后端并验证游标接口，最后发布散列资源和原子替换 React 入口。发布过程发生错误时，脚本恢复原 JAR、原入口和原发布指针。

访问 http://111.229.254.252 ，刷新后打开私信，选一个超过 30 条消息的会话，向上滚动到顶部即可自动加载更早记录；窗口会保持当前阅读位置，不再点击聊天记录页码。少于 30 条或已加载完全部消息时，顶部显示“已到最早的消息”。

线上验证结果保存于发布目录的 `cursor-verification.json` 与 `index-verification.json`；详细发布校验值和完成时间见 `deployment.json`。使用临时账号验证 65 条历史按 30 / 30 / 5 条读取，同时插入新消息后仍无重无漏，并验证新增消息增量、成员访问限制和单边清空。测试完成后删除临时账户、会话和消息。

发布于 2026-10-26 13:09（Asia/Shanghai）完成，主 JAR SHA-256 为 `084ddbfbd2a9db8c1124f14aa57a5d3d1a640e92310009e0f395b81b87d31852`。最终核对 5 份原应用配置字节保持一致，六个后端进程正常，当前核心进程无 ERROR；公网 React 入口与已验证构建一致。
