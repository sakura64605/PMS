# XXL-JOB 接入与部署

固定使用 XXL-JOB 3.1.0，执行器与调度中心同版本；保留项目 Java 17 / Spring Boot 3.1.5。
参考[官方执行器配置](https://github.com/xuxueli/xxl-job/blob/3.1.0/xxl-job-executor-samples/xxl-job-executor-sample-springboot/src/main/java/com/xxl/job/executor/core/config/XxlJobConfig.java)、
[官方调度中心配置](https://github.com/xuxueli/xxl-job/blob/3.1.0/xxl-job-admin/src/main/resources/application.properties)、
[官方数据库脚本](https://github.com/xuxueli/xxl-job/blob/3.1.0/doc/db/tables_xxl_job.sql)。

## 调度入口
| Handler | 北京时间计划 | 作用 |
| --- | --- | --- |
| pmsDueTasks | 每15秒 | 批量处理到期业务任务 |
| pmsActivityStatus | 每分钟 | 校准活动开始、结束状态 |
| pmsInboxCleanup | 每天02:00 | 清理30天前已读收件箱 |
| pmsDailyStatistics | 每天01:00 | 生成前一天统计 |
| pmsStatisticsCompensation | 03/06/09/12/15/18/21点 | 补偿遗漏统计 |

调度规则配置 FIRST 路由、DISCARD_LATER 阻塞策略、一次失败重试。
XXL-JOB 负责集中触发、日志、执行结果与重试；delay_task 继续作为业务到期记录。
没有为每个活动、公告或用户创建一条独立 cron 任务，因此仍会按计划批处理到期记录。

## 模式与执行器
pms.scheduling.mode 默认 local，可选 local / xxl；未知取值使启动失败。
local 模式只启用 LocalJobScheduler；xxl 模式只启用 XxlJobHandlers 和执行器。
旧业务类的五处 @Scheduled 和分散的 @EnableScheduling 已迁走，原方法保留供新入口调用。
xxl 模式要求非空 token。生产参数由 /opt/pms/xxl-job/executor.env 注入。
调度中心及执行器均注册本机回环地址；9999 新端口通过 systemd 网络保护单元拒绝外部访问。

## 到期任务事务
ScheduledTaskWorker 为每条任务开独立 READ_COMMITTED 事务，先锁业务行再锁任务，
与编辑/删除时“业务行→任务”的顺序一致。当前读验证任务仍到期且未完成，
业务写入和完成标记在一个事务中提交。失败回滚、保留待执行；批处理中其他任务仍可继续，
最后向 XXL 返回失败。未知类型不再被误标完成。
任务类型通过 DelayTaskExecutor 注册：活动开始、提醒、统计、报名限制结束、禁言结束、
账号限时封禁结束以及原公告发布执行器。

活动统计只把“已报名且未签到”的记录转换为爽约，排除已取消记录。
用户计数从报名数据重算；同活动已成功统计后，新重复任务不会再次惩罚或通知。
报名禁令结束时显式清空对应时间列；旧任务不会清除尚未到期的新禁令。
账号限时封禁沿用 delay_task 中的结束时间，没有新增用户表字段。
每日统计及补偿的最终错误会向外抛出，避免 XXL 错报成功。
进程内通知仍使用原提交后投递机制，本批没有增加消息可靠重放。

## 服务器目录
- /opt/pms/xxl-job/credentials.json：管理员密码、调度 token、专用库密码，600权限。
- /opt/pms/xxl-job/admin.properties：调度中心配置。
- /opt/pms/xxl-job/executor.env：PMS执行器参数。
- /opt/pms/xxl-job/admin-meta.json：执行器与5条任务ID。
- /opt/pms/xxl-job/deployment.json：实际部署版本、旧jar备份、5条任务回调验证记录。
- /etc/systemd/system/pms.service.d/xxl-job.conf：切换XXL模式的环境配置。
- /etc/systemd/system/pms-xxl-network.service：执行器网络保护。
- 独立数据库 pms_xxl_job，与业务表分离。
- 容器 pms-xxl-job-admin；调度中心本机9090，经Nginx /xxl-job-admin/ 转发。
- 后台入口：http://111.229.254.252/xxl-job-admin/

## 部署脚本与回滚
scripts/xxl-job/provision_admin.py 创建独立库、专用数据库账号、调度中心和停止状态的任务，
不自动切换PMS。官方建表脚本SHA256固定为
6cfbb5cf89b1f1c9eb1690513178f8db280dbb6681a573baab010888b903047e。
凭据从当前PMS配置读取到内存，不打印或入库到代码仓库。

scripts/xxl-job/deploy_backend.py 校验原jar及上传jar哈希，保留服务器原配置字节，
备份旧jar，切换systemd环境并重启。确认执行器注册后，逐个通过调度中心触发5条任务，
等待成功回调，才启用周期调度。任何一步失败会停止PMS调度任务、还原旧jar及调度配置。
调度中心独立保留，便于查看失败记录。

后续手动回滚：先在调度中心停止5条PMS任务，再恢复部署记录中的jar备份，
移除本批xxl-job.conf，daemon-reload并重启pms。旧部署继续使用其原有本地任务。
不要让旧jar和已启用的XXL周期任务同时运行。

## 验证
ScheduledTaskMySqlTest 使用本机 pet_system_test 的隔离数据，验证并发、事务回滚/重试、
未知与失效任务、批次故障隔离、重复统计、取消记录排除、处罚时间与空值清理。
SchedulingConfigurationTest 验证互斥模式、非法配置、固定Handler名，并启动真实执行器，
验证鉴权、触发及成功/失败回调，兼容当前项目实际依赖版本。
全量回归开启全部数据库测试开关（含 PMS_SCHEDULING_MYSQL_TESTS），排除依赖完整外围环境的 PmsApplicationTests。
