# XXL-JOB 上线记录（2026-10-24，北京时间）

- 后端源代码提交：3126f6496ef9eff4121582292485e3715a306098。
- 部署时间：2026-10-24 23:10。
- 线上 jar：/opt/pms/pms.jar。
- 旧版备份：/opt/pms/pms.jar.bak-xxl-20261024-231015。
- 构建 SHA256：088ccbfbabbe7bc773328c63e6ad98d18139553a715b18b486d30a081a41dbd6。
- 线上 SHA256：1de6245ed93e8c74a7a67d93f518bc7eb53841843ff79e5b42eb90d5c872c0fa。
  两者差异来自部署时保留服务器原 application 配置文件，已逐字节验证保留。
- 后台：http://111.229.254.252/xxl-job-admin/。
- 独立调度库：pms_xxl_job；执行器：pms-executor，分组 ID 3。
- 本次部署同时包含前面已提交的活动模块三批重构。

| Handler | 任务 ID | 首次验证日志 ID | 结果 |
| --- | --- | --- | --- |
| pmsDueTasks | 4 | 1 | 200 |
| pmsActivityStatus | 5 | 2 | 200 |
| pmsInboxCleanup | 6 | 3 | 200 |
| pmsDailyStatistics | 7 | 4 | 200 |
| pmsStatisticsCompensation | 8 | 5 | 200 |

5 条任务均先手动触发并等待成功回调，再开启周期调度。
上线后再次确认：到期任务和活动状态任务已产生后续自动调度成功记录。
PMS 与执行器网络保护服务均为 active，IPv4/IPv6 9999 外部访问限制已验证。
业务公网宠物列表、活动列表和活动详情返回成功，详情包含 React 布尔点赞字段。
调度后台公网登录页返回 HTTP 200，真实管理员登录验证通过。

验证：全量 491 个测试，0 失败、0 错误、0 跳过；
排除依赖完整外围环境的 PmsApplicationTests。新增测试含真实 MySQL 与执行器 HTTP 回调。
构建成功后保留生产配置并部署，未改业务表结构或前端构建。

管理员密码不写入本记录。服务器保存在 /opt/pms/xxl-job/credentials.json；
用户本机登录说明位于 target/xxl-job-login.txt（未入库）。
运行 python3 /opt/pms/xxl-job/verify_admin.py --live 可只读检查任务启用和成功回调。
详细部署元数据位于 /opt/pms/xxl-job/deployment.json。
后续发布应从包含本次接入的代码构建；回滚顺序见 xxl-job-integration.md。
