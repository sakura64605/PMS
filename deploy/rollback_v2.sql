-- =============================================================================
-- PMS 重构回滚脚本
--
-- 本脚本只删除列出的新增表，不修改既有表结构。
-- 代码回退、任务衔接与数据回迁必须单独核对，不能仅靠删除新增表完成。
--
-- 配套回滚步骤（完整顺序）：
--   1) 备份待删新增表的数据，停止应用；
--   2) 移除 src/main/java/com/hongjie/pms/refactor/ 及对应测试，重新构建；
--      若需恢复旧文件的原始内容，参见 docs/adr/0006-refactor-isolation-rollback.md；
--   3) 移除 pms.orm.meta-handler、pms.event.outbox.enabled 等新增配置；
--   4) 核对并迁移未完成的公告定时任务后，再启动回退版本；
--   5) 确认没有代码读取新增表后，才手工执行下面的 DROP。
--
-- 注意：本脚本会永久删除列出的新增表及其数据，不会自动回迁数据。
-- 当前业务仍使用老表，pms_pet_post 尚未切换；该表不在本脚本删除范围。
-- 若已切表，不能仅执行本脚本就宣称回滚完成，必须另行核对数据回迁。
-- =============================================================================

DROP TABLE IF EXISTS `pms_operation_log`;
DROP TABLE IF EXISTS `pms_message_template`;
DROP TABLE IF EXISTS `pms_dict_item`;
DROP TABLE IF EXISTS `pms_dict_type`;
DROP TABLE IF EXISTS `pms_domain_event`;
