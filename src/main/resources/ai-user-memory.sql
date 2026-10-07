-- ==================== AI 用户长期记忆表 ====================
-- 存储从对话中沉淀的用户持久事实/偏好（跨会话生效）。
-- 短期记忆 = 滑动窗口，走 ai_chat_message 现有表（不在此建表）。
-- 长期记忆 = 结构化事实，存本表；随会话被写入，注入每次请求的 system prompt。
-- 说明：长期记忆用 MySQL（结构化、可精确覆盖/删除）；若日后需要"语义召回"大量记忆，
--       可把本表 content 另嵌入一份到向量库，本表仍是权威源。

CREATE TABLE IF NOT EXISTS `ai_user_memory` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户',
  `content` VARCHAR(500) NOT NULL COMMENT '长期记忆内容：用户陈述的持久事实/偏好，如"养了一只橘猫叫圆圆"',
  `source_session` VARCHAR(64) DEFAULT NULL COMMENT '来源会话',
  `importance` TINYINT DEFAULT 1 COMMENT '重要度 1-5',
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0停用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_user_status_imp` (`user_id`, `status`, `importance`),
  KEY `idx_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI用户长期记忆';
