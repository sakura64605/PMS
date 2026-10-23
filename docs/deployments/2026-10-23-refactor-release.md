# 2026-10-23 重构版部署与冒烟记录

## 最终版本

- 服务器：111.229.254.252，JAR：/opt/pms/pms.jar，systemd 服务：pms。
- 业务代码提交：f684ad0；部署装配修复提交：c477002。
- 最终部署包 SHA-256：c14c4b0836c655a9eb19ea3f54bacf74c8ab528cceaae1ca288eb6e5ca436722，本地与服务器一致。
- 旧包备份：/opt/pms/pms.jar.bak-f684ad0-20261023-162913。
- 旧包 SHA-256：f98fc3ca34f2b2b86a3b75f6755d237b977bdca0c9f1a45ff8ece52965b56178。
- 保留原 prod profile、外部 /opt/pms/config/application-prod.yaml；新旧 JAR 内 application 配置摘要一致，未输出密钥。
- 本次只部署后端包，不改表、不重建前端、不推送 Git 远端。

## 部署过程及修复

首次切换后，真实启动发现 ReportTargetChangedListener 的 DistributedCache 参数有两个候选（stringRedisTemplateProxy、distributedCache），应用未就绪。立即恢复上述备份并重启，旧版本 pet/list HTTP 200，NRestarts=0。

修复明确指定 @Qualifier("distributedCache")，并将举报事务/接管测试改为同时提供两个缓存候选，验证只有指定候选被使用。随后整体测试与 package 成功：268 项，220 项通过、48 项数据库测试显式跳过；原依赖外部服务的 PmsApplicationTests 未纳入本轮本地回归。此前各数据库专项单独执行的结果见对应模块架构文档。

修复包重新上传至临时文件，校验 SHA-256 及当前旧包基线，原子替换后重启。最终运行 PID 4068020，ActiveState=active，SubState=running，NRestarts=0。

北京时间 16:38:47.788 日志确认 Started PetSystemApplication，Spring 启动耗时 24.076 秒。日志确认 NOTICE_PUBLISH 执行器注册，以及新公告服务接管后旧 Redis 消费者不启动。

## 服务器冒烟：21/21 通过

管理员登录成功后检查下列接口，HTTP 200 且业务 code=200；仅记录状态与总数，不输出 token、消息正文或用户资料：

1. user/info
2. pet/list（第一页）
3. comment/list（现有宠物帖子）
4. pet/my-posts
5. pet/favoriteList
6. notice/list
7. notice/admin/list
8. admin/audit/pending
9. admin/audit/history
10. report/list
11. following/list
12. follower/list
13. feed/home（使用超大页码，仅验证空页/总数，避免消耗正常页未读）
14. feed/unread-count
15. message/private/conversations
16. message/private/unread-count
17. message/list
18. message/unread-count
19. 匿名 message/unread-count：401
20. 匿名 message/private/unread-count：401
21. 匿名 report/list：401

同时确认服务器本机前端 `/` 与反向代理 `/pet-system/pet/list` 均返回 HTTP 200。

未执行服务器内容创建、审批、举报处置、删除或批量已读操作。登录沿用既有认证和活跃信息流程，不能把本轮描述为完全无任何运行状态写入。

## 人工验收与现有限制

完整人工操作：[71 项验收清单](../testing/refactor-acceptance-2026-10-23.md)。清单的预期不是“71 项已在线验收通过”；包含常规页面操作、跨账号验证、并发及边界测试，需用户继续执行。

明确未完成：评论删除 Controller 仍为空实现；XXL-JOB 尚未接入，定时任务仍按原 delay_task 每分钟轮询；后台账号管理、活动报名等其他模块尚未整体重写。私信删除只清本人消息，保留共享会话容器。外部消息/搜索没有完整 outbox 可靠重放保证。

## 回退

需要回退时将 /opt/pms/pms.jar.bak-f684ad0-20261023-162913 复制回 /opt/pms/pms.jar，执行 sudo systemctl restart pms，再确认旧包 SHA-256、业务接口可用及 NRestarts。无需反向数据库迁移。本轮修复版已成功启动，当前不处于回退版本。
