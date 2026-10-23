-- =============================================================================
-- PMS 重构新增表（core-v2）
--
-- 约定：
--   1. 本文件只做「新增」，绝不修改或删除任何已有表 —— 老表结构与数据保持不变，
--      因此执行 deploy/rollback_v2.sql 删除这些表后，老代码与老功能完全不受影响。
--   2. 表名统一 `pms_` 前缀，便于回滚时 grep 定位。
--   3. 全部 IF NOT EXISTS，可重复执行。
--   4. 与现有风格一致：不建外键约束，关联完整性由应用层保证（DDL 注释中标注逻辑外键）。
--
-- 为什么不复用老表：见 docs/adr 中关于 outbox / 字典 / 模板的决策记录。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 事务性事件发件箱
--
-- 替代「在 @Transactional 方法内直接发 RocketMQ」这一不安全做法：
-- 事件与业务写操作在同一事务内落库，事务提交后才由 relay 投递；
-- 业务回滚时发件箱记录随之消失，不会产生外部副作用。
--
-- 不复用 user_message：那是「发给用户的消息」业务表，往里写技术事件会污染
-- 消息列表接口（该接口已冻结）；且发件箱需要重试状态机字段，老表没有。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_domain_event`
(
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `event_id`       VARCHAR(64)  NOT NULL COMMENT '事件唯一ID(UUID)，消费端据此幂等',
    `event_type`     VARCHAR(64)  NOT NULL COMMENT '事件类型标识，如 CommentCreated',
    `payload_class`  VARCHAR(255) NOT NULL COMMENT '载荷全限定类名，供反序列化还原具体类型',
    `aggregate_type` VARCHAR(32)           DEFAULT NULL COMMENT '聚合根类型，如 Comment',
    `aggregate_id`   BIGINT                DEFAULT NULL COMMENT '聚合根主键（逻辑外键）',
    `payload`        JSON         NOT NULL COMMENT '事件载荷 JSON',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '投递状态：0-待投递 1-已投递 2-失败',
    `retry_count`    INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `next_retry_at`  DATETIME              DEFAULT NULL COMMENT '下次重试时间（退避）',
    `last_error`     VARCHAR(500)          DEFAULT NULL COMMENT '最近一次失败原因',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    `sent_at`        DATETIME              DEFAULT NULL COMMENT '投递成功时间',
    UNIQUE KEY `uk_event_id` (`event_id`),
    INDEX `idx_status_retry` (`status`, `next_retry_at`),
    INDEX `idx_aggregate` (`aggregate_type`, `aggregate_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='领域事件发件箱(新)';


-- -----------------------------------------------------------------------------
-- 2/3. 通用字典
--
-- 替代散落在 Java 与 Vue 里的硬编码：PetCategoryClassifier 写死的 0-7 物种与品种关键词、
-- CacheWarmer 写死的 List.of("领养","救助")、前端写死的标签。
--
-- 不复用 pet_category：那是宠物专属字典且结构固定，老表禁改；
-- 物种关键词、救助关键词、举报原因等跨领域字典塞不进它的列。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_dict_type`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `type_code`   VARCHAR(64)  NOT NULL COMMENT '字典类型码，如 PET_SPECIES / RESCUE_KEYWORD',
    `type_name`   VARCHAR(64)  NOT NULL COMMENT '字典类型名称',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '说明',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='字典类型(新)';

CREATE TABLE IF NOT EXISTS `pms_dict_item`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `type_code`   VARCHAR(64)  NOT NULL COMMENT '字典类型码（逻辑外键 -> pms_dict_type.type_code）',
    `item_code`   VARCHAR(64)  NOT NULL COMMENT '字典项编码',
    `item_name`   VARCHAR(128) NOT NULL COMMENT '字典项名称',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序，升序',
    `extra`       VARCHAR(512)          DEFAULT NULL COMMENT '扩展信息 JSON，如 {"petCategoryId":3}',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_type_item` (`type_code`, `item_code`),
    INDEX `idx_type_status_sort` (`type_code`, `status`, `sort`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='字典项(新)';


-- -----------------------------------------------------------------------------
-- 4. 消息模板
--
-- 把 MessageService 里 20 个 sendXxxNotification 方法硬编码的静态文案外置。
-- 注意分流原则：纯静态文案（点赞、关注）走本表；
-- 含业务计算的通知（活动统计报告要 format 出 4 个计数）仍走 NotificationHandler 策略，
-- 否则模板会退化成 SpEL，更难维护。
--
-- 不复用 user_message：那存的是消息实例，不是模板，无可复用的列。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_message_template`
(
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `code`             VARCHAR(64)   NOT NULL COMMENT '模板码，与 MessageType 枚举名对应',
    `title_template`   VARCHAR(255)  NOT NULL COMMENT '标题模板，占位符用 {xxx}',
    `content_template` VARCHAR(1000) NOT NULL COMMENT '内容模板，占位符用 {xxx}',
    `link_template`    VARCHAR(255)           DEFAULT NULL COMMENT '跳转链接模板，如 /pet/{businessId}',
    `enabled`          TINYINT       NOT NULL DEFAULT 1 COMMENT '是否启用：0-否 1-是',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='消息模板(新)';


-- -----------------------------------------------------------------------------
-- 5. 后台操作留痕
--
-- 不复用 audit_record：那是「内容审核」（用户发的帖子/活动被审核），
-- 与「后台管理员操作留痕」是完全不同的概念，混用会让审核列表出现非审核记录。
--
-- trace_id 与日志的 traceId 对齐，可用于把一次请求的所有操作串起来。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_operation_log`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `operator_id` BIGINT                DEFAULT NULL COMMENT '操作人ID（逻辑外键 -> user.id）',
    `module`      VARCHAR(64)  NOT NULL COMMENT '模块名，如 notice / admin',
    `action`      VARCHAR(64)  NOT NULL COMMENT '动作，如 PUBLISH / OFFLINE',
    `target_type` VARCHAR(32)           DEFAULT NULL COMMENT '目标类型（规范码，见 BizTargetType）',
    `target_id`   BIGINT                DEFAULT NULL COMMENT '目标主键',
    `detail`      JSON                  DEFAULT NULL COMMENT '操作明细',
    `ip`          VARCHAR(45)           DEFAULT NULL COMMENT '来源 IP',
    `trace_id`    VARCHAR(64)           DEFAULT NULL COMMENT '链路追踪 ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX `idx_module_time` (`module`, `create_time`),
    INDEX `idx_target` (`target_type`, `target_id`),
    INDEX `idx_operator_time` (`operator_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='操作日志(新)';
