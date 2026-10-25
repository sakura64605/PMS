-- =============================================================================
-- 宠物帖子新表（重构后使用）
--
-- 与老表 `pet_post` 的关系：
--   * 老表**一行不改、数据不动**，回滚时新实现撤掉即可继续用老表；
--   * 本表为后续切表准备；当前新实现仍读写 pet_post，尚未切换；
--   * 主键 id 与老表保持一致 —— comment.target_id / like_record.target_id /
--     favorite_record.target_id / user_inbox 都存的是 pet_post 的 id，
--     不做 id 映射，这些关联才能继续指向正确的帖子。
--   * 依赖的字典表 `pet_category` 属于「原本没有问题」的部分，继续复用，不重建。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 新表结构
--
-- 相比老表的四处实质改动：
--
-- 1) 逻辑删除与业务状态解耦。
--    老表用 `status = -1` 表示已删除，而 status 同时还承担
--    待审核/已发布/已完成/已下架/已拒绝五种业务含义 —— 一个字段被两种正交的
--    语义共用。后果是所有查询都必须小心地包含或排除 -1，例如老实现里
--    「查某人发布的帖子」用 in(0,1,2,3)、公开列表用 in(1,2,3)，两处都靠
--    枚举数字来排除删除态，任何新加的状态码都可能破坏这个隐式约定。
--    新表拆成 `deleted`（是否删除）+ `status`（生命周期），两者独立演进。
--
-- 2) 字段改名 pet_type -> pet_breed。
--    老表的 pet_type 存的是「橘猫」「金毛」这类**品种**自由文本，
--    而真正的「类型」是另一列 type（领养/救助）。名字与内容不符，
--    且容易与 pet_category（猫/狗等物种）混淆。新表叫 pet_breed。
--
-- 3) 新增 pet_age_months。
--    老表只有 pet_age VARCHAR(20) 存「3个月」这类自由文本，无法按年龄筛选或排序。
--    新表保留原文（展示用），另加归一化的月龄数值列（可筛选、可排序、可参与推荐）。
--
-- 4) 新增 version 乐观锁列。
--    老表没有任何并发控制。而「报名/完成/下架」这类操作是读-改-写，
--    两个管理员同时点「下架」和「完成」会互相覆盖。新表加 version，
--    由 MyBatis-Plus 的 @Version 在 UPDATE 时带上版本条件，冲突则失败而非静默覆盖。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_pet_post`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，与老表 pet_post.id 保持一致',

    -- 内容
    `user_id`         BIGINT       NOT NULL COMMENT '发布者ID（逻辑外键 -> user.id）',
    `type`            TINYINT      NOT NULL COMMENT '帖子类型：0-领养 1-救助',
    `title`           VARCHAR(100) NOT NULL COMMENT '标题',
    `content`         TEXT         NOT NULL COMMENT '正文',
    `images`          JSON                  DEFAULT NULL COMMENT '图片 URL 列表',

    -- 宠物属性
    `pet_name`        VARCHAR(50)           DEFAULT NULL COMMENT '宠物名字',
    `pet_gender`      TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0-未知 1-公 2-母',
    `pet_age_text`    VARCHAR(20)           DEFAULT NULL COMMENT '年龄原文，如「3个月」（展示用）',
    `pet_age_months`  INT                   DEFAULT NULL COMMENT '归一化月龄（筛选/排序用）；原文无法解析时为 NULL',
    `pet_category`    TINYINT      NOT NULL DEFAULT 7 COMMENT '物种分类ID（逻辑外键 -> pet_category.id）',
    `pet_breed`       VARCHAR(50)           DEFAULT NULL COMMENT '品种自由文本，如「橘猫」（老表的 pet_type）',

    -- 联系方式
    `address`         VARCHAR(200)          DEFAULT NULL COMMENT '地址',
    `contact_phone`   VARCHAR(20)  NOT NULL COMMENT '联系电话',
    `contact_wechat`  VARCHAR(50)           DEFAULT NULL COMMENT '微信号',

    -- 状态：与 deleted 完全正交
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '生命周期：0-待审核 1-已发布 2-已完成 3-已下架 4-已拒绝',
    `audit_status`    TINYINT      NOT NULL DEFAULT 0 COMMENT '审核结果：0-待审核 1-通过 2-拒绝',

    -- 计数（刻意保留冗余：读多写少，避免列表页对互动表做聚合）
    `view_count`      INT          NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `like_count`      INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count`   INT          NOT NULL DEFAULT 0 COMMENT '评论数',
    `share_count`     INT          NOT NULL DEFAULT 0 COMMENT '转发数',

    -- 审计与并发控制（对应 refactor.core.model.BaseEntity）
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除（与 status 正交）',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `creator`         BIGINT                DEFAULT NULL COMMENT '创建人；迁移数据取原发布者',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         BIGINT                DEFAULT NULL COMMENT '最后更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引按新代码的实际查询形态设计：
    --   公开列表：deleted=0 AND status IN (...) AND audit_status=1 ORDER BY create_time DESC
    --   我的发布：deleted=0 AND user_id=? AND status IN (...)
    --   回收站　：deleted=1 AND user_id=?
    INDEX `idx_feed` (`deleted`, `status`, `audit_status`, `create_time`),
    INDEX `idx_user` (`user_id`, `deleted`, `status`, `create_time`),
    INDEX `idx_type_status` (`type`, `deleted`, `status`),
    INDEX `idx_category` (`pet_category`, `deleted`, `status`, `create_time`),
    INDEX `idx_age_months` (`pet_age_months`),
    FULLTEXT KEY `ft_title_content` (`title`, `content`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='宠物帖子（重构后新表）';


-- -----------------------------------------------------------------------------
-- 数据迁移
--
-- 可重复执行：用 ON DUPLICATE KEY UPDATE，重跑不会产生重复行，
-- 也允许在切换前再跑一次把增量数据同步过去。
--
-- status 映射规则：
--   老表 -1（已删除）  -> deleted = 1，status 置 0
--                        （已删除行的 status 不再有意义，查询一律带 deleted = 0）
--   老表 0..4          -> 原样保留，deleted = 0
--
-- 老表没有 creator / updater，迁移时 creator 取发布者 user_id，updater 记 NULL
-- （不去猜测"最后是谁改的"，宁可留空也不写入错误的审计信息）。
-- -----------------------------------------------------------------------------
INSERT INTO `pms_pet_post` (`id`, `user_id`, `type`, `title`, `content`, `images`,
                            `pet_name`, `pet_gender`, `pet_age_text`, `pet_age_months`,
                            `pet_category`, `pet_breed`, `address`, `contact_phone`, `contact_wechat`,
                            `status`, `audit_status`,
                            `view_count`, `like_count`, `comment_count`, `share_count`,
                            `deleted`, `version`, `creator`, `create_time`, `updater`, `update_time`)
SELECT `id`,
       `user_id`,
       `type`,
       `title`,
       `content`,
       `images`,
       `pet_name`,
       `pet_gender`,
       `pet_age`,
       NULL,           -- pet_age_months：老数据是自由文本，不做有损猜测，留空由业务补录
       `pet_category`,
       `pet_type`,     -- 老表的 pet_type 实际存的是品种
       `address`,
       `contact_phone`,
       `contact_wechat`,
       IF(`status` = -1, 0, `status`),
       `audit_status`,
       `view_count`,
       `like_count`,
       `comment_count`,
       `share_count`,
       IF(`status` = -1, 1, 0),
       0,
       `user_id`,      -- creator：发布者
       `create_time`,
       NULL,
       `update_time`
FROM `pet_post`
ON DUPLICATE KEY UPDATE `title`         = VALUES(`title`),
                        `content`       = VALUES(`content`),
                        `images`        = VALUES(`images`),
                        `status`        = VALUES(`status`),
                        `audit_status`  = VALUES(`audit_status`),
                        `view_count`    = VALUES(`view_count`),
                        `like_count`    = VALUES(`like_count`),
                        `comment_count` = VALUES(`comment_count`),
                        `share_count`   = VALUES(`share_count`),
                        `deleted`       = VALUES(`deleted`),
                        `update_time`   = VALUES(`update_time`);


-- -----------------------------------------------------------------------------
-- 迁移校验（人工执行，两行应完全相等）
--
-- SELECT COUNT(*) AS 老表未删除 FROM `pet_post` WHERE `status` <> -1;
-- SELECT COUNT(*) AS 新表未删除 FROM `pms_pet_post` WHERE `deleted` = 0;
-- -----------------------------------------------------------------------------
