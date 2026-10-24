# 活动发布与编辑重构

## 本批接管
现有 /pet-system/activity/post 和 /pet-system/activity/update/{id} 接入 ActivityContentCommands。
沿用请求路径、原 DTO、返回结构及 activity、audit_record、delay_task 原表，旧 ActivityServiceImpl 保留。
发布身份始终来自登录上下文；编辑同时校验活动归属及请求中的 userId。
更新接口校验路径 ID 和请求体 ID 一致；请求体未传 ID 时使用路径 ID。

## 审核规则
标题、正文、图片、地点、人数或时间发生实际变化时，活动重新进入待审。
已有待审记录时复用当前轮次；没有时创建新轮次，保留历史通过和拒绝记录。
内容完全未变时不使已通过活动重新待审；若原本待审但缺记录，会补建记录。
存在多条冲突待审记录时拒绝更新并回滚。
活动标记为已结束或实际结束时间已到，均禁止编辑；已删除活动不能编辑。
沿用旧编辑流程要求新开始时间在未来的限制。

## 事务与副作用
编辑先锁活动行，与审核、报名采用一致的活动锁入口。
只写入内容和审核状态，不覆盖报名人数、点赞、浏览及评论计数。
人数上限不得低于现有报名人数。
发布时活动、审核记录、延迟任务与 Feed 数据库投影处于同一事务。
时间变化时只替换该活动尚未执行的三类活动任务，保留已执行记录及其他业务任务。
缓存在提交后失效；搜索同步通过提交后的 ID 事件重新读取已提交数据。
新开始状态 SQL 在执行时重新检查审核、删除标记和起止时间，旧任务不会提前启动改期活动。
定时结束状态 SQL 也检查当前状态和结束时间，避免旧快照覆盖修改后的状态。
本批仍使用原有数据库任务调度，没有接入 XXL-JOB。

## 验证
ActivityContentMySqlTest 使用本机 pet_system_test，覆盖发布、编辑重审、历史保留、
重复待审编辑、缺失记录修复、冲突回滚、权限、人数约束、状态与时间限制、
任务替换与回滚、控制器接管及路径校验、发布失败回滚、图片清空、编辑审核并发。
测试不变更表结构，只删除本轮临时账号所属的数据。
全量回归启用所有现有 MySQL 测试开关，排除需要完整外部环境的 PmsApplicationTests。

## 剩余范围
列表、详情、报名名单、删除和回收站仍使用旧活动服务。
后续继续迁移这些查询与命令；任务统计幂等、XXL-JOB 与通知可靠重放另行处理。


Current follow-up: all activity service operations are now taken over by PmsActivityServiceImpl. See activity-takeover-refactor.md for the final scope.
