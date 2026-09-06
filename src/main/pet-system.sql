-- 创建数据库
CREATE DATABASE IF NOT EXISTS `pet_system`
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE `pet_system`;

-- ==================== 用户相关表 ====================

-- 用户表
CREATE TABLE `user` (
                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
                        `user_name` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
                        `password` VARCHAR(255) NOT NULL COMMENT '加密后的密码',
                        `nick_name` VARCHAR(50) COMMENT '昵称',
                        `phone` VARCHAR(20) UNIQUE COMMENT '手机号',
                        `email` VARCHAR(100) UNIQUE COMMENT '邮箱',
                        `gender` TINYINT DEFAULT 0 COMMENT '性别：0-未知 1-男 2-女',
                        `avatar` VARCHAR(500) COMMENT '头像URL',
                        `signature` VARCHAR(200) COMMENT '个性签名',
                        `tags` JSON COMMENT '个人标签，如["猫奴", "救助志愿者", "宠物医生"]',
                        `privacy_settings` JSON COMMENT '隐私设置：哪些字段公开，如{"tags": true, "phone": false}',
                        `role` TINYINT DEFAULT 0 COMMENT '角色：0-普通用户 1-管理员',
                        `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
                        `searchable` TINYINT DEFAULT 1 COMMENT '是否允许被搜索：0-不允许 1-允许',
                        `ext_json` JSON COMMENT '扩展字段',
                        `remark` VARCHAR(500) COMMENT '备注',
                        `follower_count` INT DEFAULT 0 COMMENT '粉丝数',
                        `following_count` INT DEFAULT 0 COMMENT '关注数',
                        `like_count` INT DEFAULT 0 COMMENT '获赞总数',
                        `total_signups` INT DEFAULT 0 COMMENT '总报名次数',
                        `total_no_shows` INT DEFAULT 0 COMMENT '总爽约次数',
                        `recent_no_shows` INT DEFAULT 0 COMMENT '近30天爽约次数',
                        `punishment_end_time` DATETIME COMMENT '惩罚结束时间',
                        `last_activity_date` DATETIME COMMENT '最后活动日期',
                        `last_active_time` DATETIME COMMENT '最后活跃时间',
                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        INDEX `idx_username_nickname` (`user_name`, `nick_name`),
                        INDEX `idx_status_searchable` (`status`, `searchable`),
                        INDEX `idx_phone` (`phone`),
                        INDEX `idx_email` (`email`),
                        INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 头像历史表
CREATE TABLE `avatar_history` (
                                  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                  `avatar_url` VARCHAR(500) NOT NULL COMMENT '头像URL',
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  INDEX `idx_user_id` (`user_id`),
                                  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户头像历史表';

-- ==================== 宠物帖子表 ====================

-- 宠物帖子表
CREATE TABLE `pet_post` (
                            `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                            `user_id` BIGINT NOT NULL COMMENT '发布者ID',
                            `type` TINYINT NOT NULL COMMENT '类型：0-领养 1-救助',
                            `title` VARCHAR(100) NOT NULL COMMENT '标题',
                            `content` TEXT NOT NULL COMMENT '内容',
                            `images` JSON COMMENT '图片列表',
                            `pet_gender` TINYINT DEFAULT 0 COMMENT '宠物性别：0-未知 1-公 2-母',
                            `pet_age` VARCHAR(20) COMMENT '宠物年龄，如：3个月',
                            `pet_type` VARCHAR(50) COMMENT '宠物品种，如：橘猫、金毛',
                            `pet_category` TINYINT DEFAULT 7 COMMENT '物种分类：0-猫 1-狗 2-兔 3-啮齿类 4-鸟类 5-鱼类 6-爬行/两栖 7-其他',
                            `pet_name` VARCHAR(50) COMMENT '宠物名字',
                            `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                            `contact_wechat` VARCHAR(50) COMMENT '微信号',
                            `address` VARCHAR(200) COMMENT '地址',
                            `status` TINYINT DEFAULT 0 COMMENT '状态：-1-已删除 0-待审核 1-已发布 2-已完成 3-已下架 4-已拒绝',
                            `audit_status` TINYINT DEFAULT 0 COMMENT '审核状态：0-待审核 1-审核通过 2-审核拒绝',
                            `view_count` INT DEFAULT 0 COMMENT '浏览次数',
                            `like_count` INT DEFAULT 0 COMMENT '点赞数',
                            `comment_count` INT DEFAULT 0 COMMENT '评论数',
                            `share_count` INT DEFAULT 0 COMMENT '转发数',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            INDEX `idx_user_id` (`user_id`),
                            INDEX `idx_type` (`type`),
                            INDEX `idx_status` (`status`),
                            INDEX `idx_audit_status` (`audit_status`),
                            INDEX `idx_user_status` (`user_id`, `status`),
                            INDEX `idx_type_status` (`type`, `status`),
                            INDEX `idx_pet_category` (`pet_category`),
                            INDEX `idx_status_create_time` (`status`, `create_time`),
                            INDEX `idx_location` (`address`(100)),
                            FULLTEXT INDEX `ft_title_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠物帖子表';

-- ==================== 活动相关表 ====================

-- 活动表
CREATE TABLE `activity` (
                            `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '活动ID',
                            `user_id` BIGINT NOT NULL COMMENT '创建者ID',
                            `title` VARCHAR(100) NOT NULL COMMENT '活动标题',
                            `content` TEXT NOT NULL COMMENT '活动内容',
                            `images` JSON COMMENT '活动图片',
                            `location` VARCHAR(200) NOT NULL COMMENT '活动地点',
                            `max_people` INT NOT NULL COMMENT '人数限制',
                            `current_people` INT DEFAULT 0 COMMENT '当前报名人数',
                            `start_time` DATETIME NOT NULL COMMENT '开始时间',
                            `end_time` DATETIME NOT NULL COMMENT '结束时间',
                            `status` TINYINT DEFAULT 0 COMMENT '状态：0-报名中 1-进行中 2-已结束 3-已下架',
                            `audit_status` TINYINT DEFAULT 0 COMMENT '审核状态：0-待审核 1-审核通过 2-审核拒绝',
                            `view_count` INT DEFAULT 0 COMMENT '浏览次数',
                            `like_count` INT DEFAULT 0 COMMENT '点赞数',
                            `comment_count` INT DEFAULT 0 COMMENT '评论数',
                            `share_count` INT DEFAULT 0 COMMENT '分享数',
                            `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            INDEX `idx_user_id` (`user_id`),
                            INDEX `idx_status` (`status`),
                            INDEX `idx_audit_status` (`audit_status`),
                            INDEX `idx_start_time` (`start_time`),
                            INDEX `idx_user_deleted` (`user_id`, `deleted`),
                            INDEX `idx_status_start_time` (`status`, `start_time`),
                            INDEX `idx_location` (`location`(100)),
                            INDEX `idx_deleted_create_time` (`deleted`, `create_time`),
                            FULLTEXT INDEX `ft_title_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动表';

-- 活动报名表
CREATE TABLE `activity_signup` (
                                   `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报名记录ID',
                                   `activity_id` BIGINT NOT NULL COMMENT '活动ID',
                                   `user_id` BIGINT NOT NULL COMMENT '报名用户ID',
                                   `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
                                   `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                                   `remark` VARCHAR(200) COMMENT '备注',
                                   `status` TINYINT DEFAULT 1 COMMENT '状态：1-已报名 2-已取消 3-已签到 4-爽约',
                                   `check_in_time` DATETIME COMMENT '签到时间',
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
                                   `cancel_time` DATETIME COMMENT '取消时间',
                                   `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),
                                   INDEX `idx_activity_id` (`activity_id`),
                                   INDEX `idx_user_id` (`user_id`),
                                   INDEX `idx_user_status` (`user_id`, `status`),
                                   INDEX `idx_activity_status` (`activity_id`, `status`),
                                   INDEX `idx_user_activity` (`user_id`, `activity_id`),
                                   INDEX `idx_status_checkin` (`status`, `check_in_time`),
                                   INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动报名表';

-- ==================== 互动相关表 ====================

-- 评论表
CREATE TABLE `comment` (
                           `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
                           `user_id` BIGINT NOT NULL COMMENT '评论用户ID',
                           `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型: pet_post/pet_activity',
                           `target_id` BIGINT NOT NULL COMMENT '目标ID',
                           `content` TEXT NOT NULL COMMENT '评论内容',
                           `parent_id` BIGINT DEFAULT 0 COMMENT '父评论ID，0表示顶级评论',
                           `reply_to` BIGINT DEFAULT NULL COMMENT '回复的用户ID',
                           `like_count` INT DEFAULT 0 COMMENT '点赞数',
                           `status` TINYINT DEFAULT 1 COMMENT '状态：1-正常 0-已删除',
                           `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           INDEX `idx_target_parent` (`target_type`, `target_id`, `parent_id`, `status`),
                           INDEX `idx_parent_id` (`parent_id`),
                           INDEX `idx_user_id` (`user_id`),
                           INDEX `idx_target_create_time` (`target_type`, `target_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 点赞记录表
CREATE TABLE `like_record` (
                               `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
                               `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型: pet_post/pet_comment/pet_activity',
                               `target_id` BIGINT NOT NULL COMMENT '目标ID',
                               `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
                               INDEX `idx_target` (`target_type`, `target_id`),
                               INDEX `idx_user` (`user_id`),
                               INDEX `idx_target_user` (`target_type`, `target_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞记录表';

-- 收藏记录表
CREATE TABLE `favorite_record` (
                                   `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   `user_id` BIGINT NOT NULL COMMENT '收藏用户ID',
                                   `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型: pet_post/pet_activity',
                                   `target_id` BIGINT NOT NULL COMMENT '目标ID',
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
                                   INDEX `idx_target` (`target_type`, `target_id`),
                                   INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏记录表';

-- 关注关系表
CREATE TABLE `follow` (
                          `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                          `follower_id` BIGINT NOT NULL COMMENT '关注者ID',
                          `following_id` BIGINT NOT NULL COMMENT '被关注者ID',
                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
                          INDEX `idx_follower` (`follower_id`),
                          INDEX `idx_following` (`following_id`),
                          INDEX `idx_following_follower` (`following_id`, `follower_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注关系表';

-- ==================== 消息相关表 ====================

-- 站内消息表
CREATE TABLE `user_message` (
                                `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                                `user_id` BIGINT NOT NULL COMMENT '接收者用户ID',
                                `sender_id` BIGINT DEFAULT NULL COMMENT '发送者用户ID',
                                `type` VARCHAR(20) NOT NULL COMMENT '消息类型：LIKE,COMMENT,FOLLOW,SIGN_UP,ACTIVITY_REMINDER,AUDIT_PASS,AUDIT_REJECT,PUNISHMENT,SYSTEM',
                                `title` VARCHAR(100) DEFAULT NULL COMMENT '消息标题',
                                `content` TEXT NOT NULL COMMENT '消息内容',
                                `business_id` BIGINT DEFAULT NULL COMMENT '业务ID（活动ID/评论ID等）',
                                `link` VARCHAR(255) DEFAULT NULL COMMENT '跳转链接',
                                `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
                                `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
                                `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                INDEX `idx_user_id` (`user_id`),
                                INDEX `idx_user_read` (`user_id`, `is_read`),
                                INDEX `idx_user_type` (`user_id`, `type`),
                                INDEX `idx_user_read_time` (`user_id`, `is_read`, `create_time`),
                                INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息记录表';

-- 私信会话表
CREATE TABLE `private_conversation` (
                                        `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
                                        `user_a` BIGINT NOT NULL COMMENT '用户A（较小的用户ID）',
                                        `user_b` BIGINT NOT NULL COMMENT '用户B（较大的用户ID）',
                                        `last_message` VARCHAR(500) COMMENT '最后一条消息',
                                        `last_message_time` DATETIME COMMENT '最后消息时间',
                                        `unread_count_a` INT DEFAULT 0 COMMENT 'A未读数',
                                        `unread_count_b` INT DEFAULT 0 COMMENT 'B未读数',
                                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        UNIQUE KEY `uk_users` (`user_a`, `user_b`),
                                        INDEX `idx_user_a` (`user_a`),
                                        INDEX `idx_user_b` (`user_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话表';

-- 私信消息表
CREATE TABLE `private_message` (
                                   `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
                                   `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
                                   `from_user_id` BIGINT NOT NULL COMMENT '发送者用户ID',
                                   `to_user_id` BIGINT NOT NULL COMMENT '接收者用户ID',
                                   `message_type` TINYINT DEFAULT 1 COMMENT '消息类型：1-文本 2-图片',
                                   `content` VARCHAR(1000) NOT NULL COMMENT '消息内容',
                                   `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
                                   `is_deleted_by_from` TINYINT DEFAULT 0 COMMENT '发送者是否删除',
                                   `is_deleted_by_to` TINYINT DEFAULT 0 COMMENT '接收者是否删除',
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   INDEX `idx_conversation` (`conversation_id`, `create_time`),
                                   INDEX `idx_from_user` (`from_user_id`),
                                   INDEX `idx_to_user` (`to_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息表';

-- ==================== Feed 流相关表 ====================

-- 用户收件箱（存储推模式的消息）
CREATE TABLE `user_inbox` (
                              `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                              `user_id` BIGINT NOT NULL COMMENT '收件人用户ID',
                              `post_id` BIGINT NOT NULL COMMENT '帖子ID',
                              `post_type` VARCHAR(20) NOT NULL COMMENT '帖子类型: pet/activity',
                              `poster_id` BIGINT NOT NULL COMMENT '发布者用户ID',
                              `poster_name` VARCHAR(50) COMMENT '发布者昵称（冗余）',
                              `poster_avatar` VARCHAR(255) COMMENT '发布者头像（冗余）',
                              `title` VARCHAR(200) COMMENT '帖子标题（冗余）',
                              `cover_image` VARCHAR(255) COMMENT '封面图（冗余）',
                              `create_time` DATETIME NOT NULL COMMENT '帖子创建时间',
                              `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
                              `read_time` DATETIME COMMENT '阅读时间',
                              INDEX `idx_user_time` (`user_id`, `create_time`),
                              INDEX `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收件箱';

-- 大V配置表
CREATE TABLE `big_v_config` (
                                `user_id` BIGINT PRIMARY KEY COMMENT '用户ID',
                                `fans_count` INT DEFAULT 0 COMMENT '粉丝数',
                                `use_pull_mode` TINYINT DEFAULT 1 COMMENT 'Feed模式：1-拉模式(大V) 0-推模式(普通用户)',
                                `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                INDEX `idx_fans_count` (`fans_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大V配置表';

-- 用户收件箱配置
CREATE TABLE `user_inbox_config` (
                                     `user_id` BIGINT PRIMARY KEY COMMENT '用户ID',
                                     `max_keep_days` INT DEFAULT 30 COMMENT '最大保留天数',
                                     `auto_clean` TINYINT DEFAULT 1 COMMENT '是否自动清理：0-否 1-是',
                                     `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收件箱配置';

-- ==================== 公告相关表 ====================

-- 系统公告表
CREATE TABLE `notice` (
                          `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '公告ID',
                          `title` VARCHAR(100) NOT NULL COMMENT '公告标题',
                          `content` TEXT NOT NULL COMMENT '公告内容',
                          `type` TINYINT DEFAULT 1 COMMENT '类型：1-系统公告 2-活动通知 3-重要提醒',
                          `priority` TINYINT DEFAULT 0 COMMENT '优先级：0-普通 1-重要 2-紧急',
                          `status` TINYINT DEFAULT 1 COMMENT '状态：0-草稿 1-已发布 2-已下线',
                          `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
                          `publish_time` DATETIME COMMENT '发布时间',
                          `expire_time` DATETIME COMMENT '过期时间',
                          `create_by` BIGINT COMMENT '创建人ID',
                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
                          INDEX `idx_status_publish_time` (`status`, `publish_time`),
                          INDEX `idx_top_priority` (`is_top`, `priority`, `publish_time`),
                          INDEX `idx_status_keyword` (`status`, `title`(50))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告表';

-- 公告已读记录表
CREATE TABLE `notice_read_record` (
                                      `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                      `notice_id` BIGINT NOT NULL COMMENT '公告ID',
                                      `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                      `read_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
                                      UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告已读记录表';

-- ==================== 审核相关表 ====================

-- 审核记录表
CREATE TABLE `audit_record` (
                                `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审核记录ID',
                                `target_type` VARCHAR(50) NOT NULL COMMENT '目标类型: adopt/help/activity',
                                `target_id` BIGINT NOT NULL COMMENT '目标ID',
                                `audit_status` TINYINT DEFAULT 0 COMMENT '审核状态: 0-待审核 1-审核通过 2-审核拒绝',
                                `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT '拒绝原因',
                                `auditor_id` BIGINT DEFAULT NULL COMMENT '审核人ID',
                                `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
                                `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                INDEX `idx_target` (`target_type`, `target_id`),
                                INDEX `idx_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核记录表';

-- ==================== 延时任务表 ====================

-- 延时任务表
CREATE TABLE `delay_task` (
                              `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
                              `task_type` VARCHAR(50) NOT NULL COMMENT '任务类型: ACTIVITY_REMIND, ACTIVITY_STATISTICS',
                              `business_id` BIGINT NOT NULL COMMENT '业务ID',
                              `execute_time` DATETIME NOT NULL COMMENT '执行时间',
                              `status` TINYINT DEFAULT 0 COMMENT '状态：0-待执行 1-已执行 2-执行失败',
                              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              INDEX `idx_execute_time` (`execute_time`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='延时任务表';

ALTER TABLE `user`
    ADD COLUMN `is_muted` TINYINT DEFAULT 0 COMMENT '是否禁言: 0-正常 1-禁言',
ADD COLUMN `mute_end_time` DATETIME DEFAULT NULL COMMENT '禁言结束时间',
ADD COLUMN `is_banned_signup` TINYINT DEFAULT 0 COMMENT '是否禁止报名: 0-正常 1-禁止报名',
ADD COLUMN `ban_signup_end_time` DATETIME DEFAULT NULL COMMENT '禁止报名结束时间';

-- 举报记录表
CREATE TABLE `report_record` (
                                 `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 `reporter_id` BIGINT NOT NULL COMMENT '举报人ID',
                                 `target_type` VARCHAR(50) NOT NULL COMMENT '目标类型: pet/activity/comment/user',
                                 `target_id` BIGINT NOT NULL COMMENT '目标ID',
                                 `reason` VARCHAR(200) NOT NULL COMMENT '举报原因',
                                 `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待处理 1-已处理 2-已驳回',
                                 `handler_id` BIGINT DEFAULT NULL COMMENT '处理人ID',
                                 `handle_result` VARCHAR(200) DEFAULT NULL COMMENT '处理结果',
                                 `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 INDEX idx_target (target_type, target_id),
                                 INDEX idx_status (status),
                                 INDEX idx_reporter (reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报记录表';

-- 日常表
CREATE TABLE `daily_post` (
                              `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                              `user_id` BIGINT NOT NULL,
                              `content` TEXT NOT NULL COMMENT '文字内容',
                              `images` JSON COMMENT '图片列表',
                              `video_url` VARCHAR(500) COMMENT '视频URL',
                              `topic_id` BIGINT COMMENT '话题ID',
                              `location` VARCHAR(200) COMMENT '位置',
                              `view_count` INT DEFAULT 0,
                              `like_count` INT DEFAULT 0,
                              `comment_count` INT DEFAULT 0,
                              `share_count` INT DEFAULT 0,
                              `status` TINYINT DEFAULT 1 COMMENT '1-正常 0-删除',
                              `audit_status` TINYINT DEFAULT 0 COMMENT '0-待审核 1-通过 2-拒绝',
                              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                              INDEX idx_user (user_id),
                              INDEX idx_topic (topic_id),
                              INDEX idx_time (create_time),
                              INDEX idx_hot (like_count, view_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日常表';

-- 话题表
CREATE TABLE `topic` (
                         `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                         `name` VARCHAR(100) NOT NULL COMMENT '话题名称，如：#猫咪日常#',
                         `description` VARCHAR(500) COMMENT '话题描述',
                         `post_count` INT DEFAULT 0 COMMENT '帖子数',
                         `view_count` INT DEFAULT 0 COMMENT '浏览量',
                         `hot_score` DOUBLE DEFAULT 0 COMMENT '热度分',
                         `status` TINYINT DEFAULT 1 COMMENT '1-正常 0-删除',
                         `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                         UNIQUE KEY uk_name (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 日常-话题关联表
CREATE TABLE `daily_topic_rel` (
                                   `daily_id` BIGINT NOT NULL,
                                   `topic_id` BIGINT NOT NULL,
                                   PRIMARY KEY (daily_id, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 1. 用户行为日志表
CREATE TABLE `daily_user_behavior` (
                                       `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       `user_id` BIGINT NOT NULL,
                                       `target_id` BIGINT NOT NULL,
                                       `action_type` VARCHAR(20) NOT NULL COMMENT 'view/like/share/follow',
                                       `action_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                       INDEX idx_user (user_id, action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 用户兴趣画像表
CREATE TABLE `daily_user_interest` (
                                       `user_id` BIGINT PRIMARY KEY,
                                       `interest_json` TEXT COMMENT '{"cat":0.8, "dog":0.2, "topic_1":0.6}',
                                       `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 日常帖子特征表
CREATE TABLE `daily_item_feature` (
                                      `daily_id` BIGINT PRIMARY KEY,
                                      `topic_ids` VARCHAR(500) COMMENT '话题ID列表',
                                      `topic_tags` VARCHAR(500) COMMENT '话题标签',
                                      `hot_score` DOUBLE DEFAULT 0,
                                      `fresh_score` DOUBLE DEFAULT 0,
                                      `quality_score` DOUBLE DEFAULT 0,
                                      `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 每日统计数据表
CREATE TABLE `daily_statistics` (
                                    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `stat_date` DATE NOT NULL COMMENT '统计日期',

    -- 用户统计
                                    `new_user_count` INT DEFAULT 0 COMMENT '新增用户数',
                                    `total_user_count` INT DEFAULT 0 COMMENT '累计用户数',
                                    `active_user_count` INT DEFAULT 0 COMMENT '活跃用户数',
                                    `dau` INT DEFAULT 0 COMMENT '日活跃用户',
                                    `wau` INT DEFAULT 0 COMMENT '周活跃用户',
                                    `mau` INT DEFAULT 0 COMMENT '月活跃用户',

    -- 内容统计
                                    `new_pet_post_count` INT DEFAULT 0 COMMENT '新增宠物帖子数',
                                    `total_pet_post_count` INT DEFAULT 0 COMMENT '累计宠物帖子数',
                                    `new_activity_count` INT DEFAULT 0 COMMENT '新增活动数',
                                    `total_activity_count` INT DEFAULT 0 COMMENT '累计活动数',
                                    `new_daily_post_count` INT DEFAULT 0 COMMENT '新增日常动态数',
                                    `total_daily_post_count` INT DEFAULT 0 COMMENT '累计日常动态数',
                                    `new_comment_count` INT DEFAULT 0 COMMENT '新增评论数',
                                    `total_comment_count` INT DEFAULT 0 COMMENT '累计评论数',

    -- 互动统计
                                    `new_like_count` INT DEFAULT 0 COMMENT '新增点赞数',
                                    `total_like_count` INT DEFAULT 0 COMMENT '累计点赞数',
                                    `new_follow_count` INT DEFAULT 0 COMMENT '新增关注数',
                                    `total_follow_count` INT DEFAULT 0 COMMENT '累计关注数',
                                    `new_favorite_count` INT DEFAULT 0 COMMENT '新增收藏数',
                                    `total_favorite_count` INT DEFAULT 0 COMMENT '累计收藏数',
                                    `new_share_count` INT DEFAULT 0 COMMENT '新增分享数',
                                    `total_share_count` INT DEFAULT 0 COMMENT '累计分享数',

    -- 活动统计
                                    `new_signup_count` INT DEFAULT 0 COMMENT '新增报名数',
                                    `total_signup_count` INT DEFAULT 0 COMMENT '累计报名数',
                                    `new_checkin_count` INT DEFAULT 0 COMMENT '新增签到数',
                                    `total_checkin_count` INT DEFAULT 0 COMMENT '累计签到数',

    -- 审核统计
                                    `pending_audit_count` INT DEFAULT 0 COMMENT '待审核数量',
                                    `approved_count` INT DEFAULT 0 COMMENT '审核通过数',
                                    `rejected_count` INT DEFAULT 0 COMMENT '审核拒绝数',

    -- 举报统计
                                    `new_report_count` INT DEFAULT 0 COMMENT '新增举报数',
                                    `pending_report_count` INT DEFAULT 0 COMMENT '待处理举报数',
                                    `handled_report_count` INT DEFAULT 0 COMMENT '已处理举报数',

    -- 私信统计
                                    `new_private_message_count` INT DEFAULT 0 COMMENT '新增私信数',
                                    `total_private_message_count` INT DEFAULT 0 COMMENT '累计私信数',

                                    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_stat_date` (`stat_date`),
                                    KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计数据表';

-- 会话表
CREATE TABLE `ai_chat_session` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `session_id` varchar(64) NOT NULL,
                                   `user_id` bigint DEFAULT NULL,
                                   `title` varchar(200) DEFAULT NULL,
                                   `status` tinyint DEFAULT '1',
                                   `message_count` int DEFAULT '0',
                                   `source` varchar(20) DEFAULT 'web',
                                   `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                   `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_session_id` (`session_id`),
                                   KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE `ai_chat_message` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `session_id` varchar(64) NOT NULL,
                                   `message_id` varchar(64) NOT NULL,
                                   `role` varchar(20) NOT NULL,
                                   `content` text,
                                   `tool_calls` json DEFAULT NULL,
                                   `tool_call_id` varchar(64) DEFAULT NULL,
                                   `tokens_used` int DEFAULT '0',
                                   `latency_ms` int DEFAULT '0',
                                   `feedback` tinyint DEFAULT NULL,
                                   `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库表
CREATE TABLE `ai_knowledge_base` (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `doc_id` varchar(64) NOT NULL,
                                     `title` varchar(500) NOT NULL,
                                     `content` text NOT NULL,
                                     `content_type` varchar(50) DEFAULT 'faq',
                                     `category` varchar(100) DEFAULT NULL,
                                     `tags` json DEFAULT NULL,
                                     `status` tinyint DEFAULT '1',
                                     `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                     `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 人工转接表
CREATE TABLE `ai_human_transfer` (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `session_id` varchar(64) NOT NULL,
                                     `user_id` bigint NOT NULL,
                                     `admin_id` bigint DEFAULT NULL,
                                     `reason` varchar(500) DEFAULT NULL,
                                     `status` tinyint DEFAULT '1',
                                     `transferred_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                     `closed_at` datetime DEFAULT NULL,
                                     PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;