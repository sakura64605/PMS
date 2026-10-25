# 模块化后端发布（2026-10-25）

已将代码版本 `15af9be` 部署到 `111.229.254.252:/opt/pms/pms.jar` 并重启 `pms`。前端入口 http://111.229.254.252/ 。本次保留线上应用配置、静态资源、XXL-JOB 配置/环境和网络保护规则，不修改数据库表结构。

构建包 SHA-256：`0ba3147c2cac01311d31ee4f3c2368d7dc74662933ab0c048e4389b66baa26f8`。
实际部署包 SHA-256（保留线上配置及静态资源后）：`7cd1c79b8990ac51e9ca5f44de4b7e9f8cbd55c00e9a8f1effed4f75a6d160f5`。
旧包备份：`/opt/pms/pms.jar.bak-modules-20261025-200417`。
发布记录：`/opt/pms/releases/modules-15af9be-20261025/`；当前版本指针：`/opt/pms/releases/current.json`。

## 验证

- 29 项服务器核心检查通过，包括读取、注册/资料、发布审核、搜索可见性、点赞取消点赞、评论计数、改密失效和退出登录。
- 10 组公网 AI/接口检查通过，包括真实消息 ID、反馈、越权拒绝、转人工幂等、会话删除和真实 WebSocket；没有调用外部大模型。
- 5 个 XXL-JOB handler 手动触发及回调均为 200，恢复原启用状态后，到期任务与活动状态又观察到自动成功回调。
- 搜索重建成功，处理 165 条记录，增量失败数为 0；当前别名指向 `pms_search_bf9c7449a5774aba99fec27193851182`。
- 预检查令牌已撤销，临时业务账号、帖子和 AI 记录已清理，临时 ES 文档为 0。网页及公网宠物、活动列表返回成功。

## Nginx 修复

公网 AI WebSocket 初次验证出现握手升级头为空。原 Nginx 只为 `/pet-system/ws/` 设置 Upgrade 转发，`/pet-system/ai/ws` 落入普通 HTTP 代理。

已在 `/etc/nginx/conf.d/pms.conf` 增加 AI WebSocket 精确匹配，转发 HTTP/1.1、Upgrade 和 Connection，执行 `nginx -t` 后平滑重载。公网真实连接复测通过。当前后端进程只有该次已修复的握手 ERROR，没有其他 ERROR。

Nginx 原配置备份在本次发布目录 `nginx-before.conf`，更新记录在 `nginx-update.json`。可复用规则见 `ops/nginx/ai-websocket.location.conf`，应放入已有 PMS 的 server 块；不是完整站点配置。
