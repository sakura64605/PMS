# 统计模块重构

## 范围与接入

保留 daily_statistics 及全部业务表、实体、请求/响应 DTO、HTTP 路径和旧 StatisticsService。
新 PmsStatisticsService 使用 @Primary，覆盖旧类全部 11 个公开业务方法；
原控制器及 PmsJobActions 注入的 StatisticsService 都会调用新实现。
控制器只把默认日期改为共用的 StatisticsCalendar，采用 Asia/Shanghai 时区。

## 职责与设计

- StatisticsQueries：有管理员校验的日报、周报、月报、年报、区间和实时查询。
- StatisticsAssembler：统一空值处理、日历补零、趋势和汇总。
- StatisticsContributor：按用户、内容、互动、报名、治理、私信注册的统计策略。
- StatisticsCollector：执行全部策略，重复策略名称启动即报错。
- StatisticsFactMapper / StatisticsSql：受封闭枚举约束的事实聚合 SQL；表名不来自请求。
- StatisticsDayWriter：按日期加数据库排他锁，独立事务生成或替换一条完整快照。
- StatisticsGeneration：每日生成、缺口补偿、手工重建的编排。
- StatisticsCache：提交后清理旧 statistics:* 缓存；新查询不再缓存携带旧数值的响应 DTO。

XXL-JOB 仍使用原 pmsDailyStatistics、pmsStatisticsCompensation handler；
两个调度入口共用 pms:scheduled:statistics 看门狗锁，无需修改任务定义。
手工重建和任务并发时，数据库唯一日期索引和行锁保证单日串行。

## 计数与查询口径

统计日期区间统一为 [当天零点，次日零点)，今日查询截止当前时间。
新增数按 create_time，历史累计只读取截止时间之前创建且仍存在的记录。
签到新增按 check_in_time；审核通过/拒绝按 audit_time，
历史缺失决策时间的记录回退 create_time。
举报已处理使用状态 1，驳回状态 2 不计为已处理。
期末待审/待处理包括截止时仍未决，以及处理时间在截止之后的记录。

WAU 为当天及之前 6 天，MAU 为当天及之前 29 天。
日报趋势 30 个日历点；周为周一至周日；月/年覆盖完整日历。
缺少快照的日期按零展示，均值包含这些日期，区间标签不随已有数据缩短。
包含今日的查询以实时聚合替换今日快照，避免部分生成数据或旧缓存影响结果。
无上期值时，增长率为当前非零 100%、当前为零 0%。

## 数据本身的限制

旧模型只有 user.last_active_time，没有按日活跃事件日志。
DAU/WAU/MAU 及周期活跃人数依然是“最后活跃时间落在区间”的近似口径；
后来再次登录会覆盖历史，补录不能还原真实历史活跃用户。
年报月均活跃人数为该口径年活跃人数除以 12。
物理删除的点赞、关注等也无法从当前表还原历史累计和新增。
已保存的历史快照保留，普通任务不会覆盖；手工重建使用当前可观测记录。
没有分享事件来源，分享数继续为零。本轮不增加事件日志表或伪造历史。

## 生成、补偿与失败处理

每日生成只补昨天，手工重建仅允许已结束日期；今日用实时接口。
单次手工范围最多 366 天；查询范围最多 3660 天。
补偿扫描窗口内全部缺失日期，修复中间缺口，并支持空快照库首次生成。

可选配置：
- pms.statistics.compensation-lookback-days：默认 90，限制 1～3660。
- pms.statistics.compensation-batch-days：默认 31，限制 1～366。

补偿从窗口内最早缺失日开始，后续调度继续处理剩余缺口；
超过回看窗口的历史需要显式手工补录。
每一天独立提交。某天失败不会回滚其他日期，但本次任务报错，便于 XXL-JOB 告警和重试。
不先删除旧快照，失败保留原行和数值。新日期占位与计算更新在同一事务内，
占位的空时间戳不会在事务外可见；提交时写完整计数及时间。
使用 duplicate-key no-op 直接获得排他锁，避免 INSERT IGNORE 共享锁升级导致死锁。

## 验证与交付（2026-10-25）

只运行相关测试，无全量回归：

1. 统计查询/编排/兼容适配器、旧统计缓存回归、XXL 配置与互斥：33 个测试全部通过。
2. 补充逐日事务和补偿重试后，只重跑 StatisticsModuleMySqlTest：8 个全部通过。
3. 统一控制器日期后，只跑 StatisticsControllerTest：1 个通过。

去重合计 35 个相关测试。真实 MySQL 测试连接本机 pet_system_test，
覆盖零点边界、历史累计、7/30 天窗口、决策时间、举报状态、
并发生成/重建、失败回滚、补偿部分成功及只重试缺口。
测试窗口先验证为空，只清理本次专用日期窗口和记录 ID，不清空业务表。

运行数据库测试须显式设置 PMS_STATISTICS_MYSQL_TESTS=true、
PMS_TEST_DB_PASSWORD；缺少授权开关则跳过数据库测试。
完整相关测试选择器：
mvn -s target/report-build-settings.xml "-Dtest=*Statistics*,SchedulingConfigurationTest,PmsJobActionsTest" test

未部署，本轮只提交源码、相关测试及说明。
