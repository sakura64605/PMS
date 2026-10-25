use pet_system;

CREATE TABLE user (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
                      username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
                      password VARCHAR(255) NOT NULL COMMENT '加密后的密码',
                      nickname VARCHAR(50) COMMENT '昵称',
                      phone VARCHAR(20) UNIQUE COMMENT '手机号',
                      email VARCHAR(100) UNIQUE COMMENT '邮箱',
                      gender TINYINT DEFAULT 0 COMMENT '性别：0-未知 1-男 2-女',
                      avatar VARCHAR(500) COMMENT '头像URL',
                      signature VARCHAR(200) COMMENT '个性签名',
                      tags JSON COMMENT '个人标签，如["猫奴", "救助志愿者", "宠物医生"]',
                      privacy_settings JSON COMMENT '隐私设置：哪些字段公开，如{"tags": true, "phone": false}',
                      role TINYINT DEFAULT 0 COMMENT '角色：0-普通用户 1-管理员',
                      status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
                      ext_json JSON COMMENT '扩展字段',
                      remark VARCHAR(500) COMMENT '备注',
                      create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

INSERT INTO user (username, password, nickname, role, tags, privacy_settings)
VALUES (
           'admin',
           '请替换为加密后的密码',
           '系统管理员',
           1,
           '["管理员", "宠物爱好者"]',
           '{"tags": true, "phone": false, "email": false}'
       );

SELECT MAX(id) FROM user;

-- 2. 重置自增值为最大ID+1
ALTER TABLE user AUTO_INCREMENT = 1;

ALTER TABLE user CHANGE nickname nick_name VARCHAR(50) COMMENT '昵称';

ALTER TABLE user
    ADD COLUMN `searchable` TINYINT(1) DEFAULT 1 COMMENT '是否允许被搜索：0-不允许 1-允许';

-- 头像历史表
CREATE TABLE avatar_history (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                user_id BIGINT NOT NULL COMMENT '用户ID',
                                avatar_url VARCHAR(500) NOT NULL COMMENT '头像URL',
                                is_current TINYINT DEFAULT 0 COMMENT '是否为当前头像：0-历史 1-当前',
                                create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                INDEX idx_user_id (user_id),
                                INDEX idx_is_current (is_current)
) COMMENT='用户头像历史表';

ALTER TABLE avatar_history DROP COLUMN is_current;

-- 宠物信息表（已有）
CREATE TABLE pet_post (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          user_id BIGINT NOT NULL COMMENT '发布者ID',
                          type TINYINT NOT NULL COMMENT '类型：0-领养 1-救助',
                          title VARCHAR(100) NOT NULL COMMENT '标题',
                          content TEXT NOT NULL COMMENT '内容',
                          images JSON COMMENT '图片列表',
                          pet_gender TINYINT COMMENT '宠物性别：0-未知 1-公 2-母',
                          pet_age VARCHAR(20) COMMENT '宠物年龄，如：3个月',
                          pet_type VARCHAR(50) COMMENT '宠物品种，如：橘猫、金毛',
                          pet_name VARCHAR(50) COMMENT '宠物名字',
                          contact_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
                          contact_wechat VARCHAR(50) COMMENT '微信号',
                          address VARCHAR(200) COMMENT '地址',
                          status TINYINT DEFAULT 0 COMMENT '状态：0-待审核 1-已发布 2-已领养/已完成 3-已下架',
                          view_count INT DEFAULT 0 COMMENT '浏览次数',
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                          update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

ALTER TABLE pet_post
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '状态：-1-已删除 0-待审核 1-已发布 2-已完成 3-已下架 4-已拒绝';

ALTER TABLE user
    ADD COLUMN last_active_time DATETIME COMMENT '最后活跃时间'
AFTER create_time;
-- 添加统计字段
ALTER TABLE `user`
    ADD COLUMN `post_count` INT NOT NULL DEFAULT 0 COMMENT '发布数（宠物+活动）',
ADD COLUMN `follower_count` INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
ADD COLUMN `following_count` INT NOT NULL DEFAULT 0 COMMENT '关注数',
ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT '获赞总数';
ALTER TABLE `user` DROP COLUMN `post_count`;

-- 给 pet_post 表添加互动统计字段
ALTER TABLE `pet_post`
    ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
ADD COLUMN `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数',
ADD COLUMN `share_count` INT NOT NULL DEFAULT 0 COMMENT '转发数';

CREATE TABLE `like_record` (
                               `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
                               `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型: pet/activity/comment',
                               `target_id` BIGINT NOT NULL COMMENT '目标ID',
                               `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
                               INDEX `idx_target` (`target_type`, `target_id`),
                               INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';

CREATE TABLE `favorite_record` (
                                   `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   `user_id` BIGINT NOT NULL COMMENT '收藏用户ID',
                                   `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型: pet/activity',
                                   `target_id` BIGINT NOT NULL COMMENT '目标ID',
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
                                   INDEX `idx_target` (`target_type`, `target_id`),
                                   INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏记录表';

CREATE TABLE `activity` (
                            `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                            `user_id` BIGINT NOT NULL COMMENT '创建者ID',
                            `title` VARCHAR(100) NOT NULL COMMENT '活动标题',
                            `content` TEXT NOT NULL COMMENT '活动内容',
                            `images` JSON COMMENT '活动图片',
                            `location` VARCHAR(200) NOT NULL COMMENT '活动地点',
                            `max_people` INT NOT NULL COMMENT '人数限制',
                            `current_people` INT DEFAULT 0 COMMENT '当前报名人数',
                            `start_time` DATETIME NOT NULL COMMENT '开始时间',
                            `end_time` DATETIME NOT NULL COMMENT '结束时间',
                            `status` TINYINT DEFAULT 0 COMMENT '状态：0-报名中 1-进行中 2-已结束 3-已取消',
                            `view_count` INT DEFAULT 0 COMMENT '浏览次数',
                            `like_count` INT DEFAULT 0 COMMENT '点赞数',
                            `comment_count` INT DEFAULT 0 COMMENT '评论数',
                            `share_count` INT DEFAULT 0 COMMENT '分享数',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                            `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `user`
    ADD COLUMN `total_signups` INT DEFAULT 0 COMMENT '总报名次数',
ADD COLUMN `total_no_shows` INT DEFAULT 0 COMMENT '总爽约次数',
ADD COLUMN `recent_no_shows` INT DEFAULT 0 COMMENT '近30天爽约次数',
ADD COLUMN `punishment_end_time` DATETIME DEFAULT NULL COMMENT '惩罚结束时间',
ADD COLUMN `last_activity_date` DATETIME DEFAULT NULL COMMENT '最后活动日期';

CREATE TABLE `activity_signup` (
                                   `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                   `activity_id` BIGINT NOT NULL COMMENT '活动ID',
                                   `user_id` BIGINT NOT NULL COMMENT '报名用户ID',
                                   `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
                                   `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                                   `remark` VARCHAR(200) COMMENT '备注',
                                   `status` TINYINT DEFAULT 1 COMMENT '状态：1-已报名 2-已取消 3-已签到 4-爽约',
                                   `check_in_time` DATETIME COMMENT '签到时间',
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
                                   `cancel_time` DATETIME COMMENT '取消时间',
                                   `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),
                                   INDEX `idx_activity` (`activity_id`),
                                   INDEX `idx_user_status` (`user_id`, `status`),
                                   INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `comment` (
                           `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                           `user_id` BIGINT NOT NULL COMMENT '评论用户ID',
                           `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型: pet/activity',
                           `target_id` BIGINT NOT NULL COMMENT '目标ID',
                           `content` TEXT NOT NULL COMMENT '评论内容',
                           `parent_id` BIGINT DEFAULT 0 COMMENT '父评论ID，0表示顶级评论',
                           `reply_to` BIGINT DEFAULT NULL COMMENT '回复的用户ID',
                           `like_count` INT DEFAULT 0 COMMENT '点赞数',
                           `status` TINYINT DEFAULT 1 COMMENT '1-正常 0-已删除',
                           `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                           `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE user
    MODIFY COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP
    COMMENT '更新时间';

CREATE TABLE `follow` (
                          `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                          `follower_id` BIGINT NOT NULL COMMENT '关注者ID',
                          `following_id` BIGINT NOT NULL COMMENT '被关注者ID',
                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                          UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
                          INDEX `idx_follower` (`follower_id`),
                          INDEX `idx_following` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

CREATE TABLE `notice` (
                          `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                          `title` VARCHAR(100) NOT NULL COMMENT '公告标题',
                          `content` TEXT NOT NULL COMMENT '公告内容',
                          `type` TINYINT DEFAULT 1 COMMENT '类型：1-系统公告 2-活动通知 3-重要提醒',
                          `priority` TINYINT DEFAULT 0 COMMENT '优先级：0-普通 1-重要 2-紧急',
                          `status` TINYINT DEFAULT 1 COMMENT '状态：0-草稿 1-已发布 2-已下线',
                          `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
                          `publish_time` DATETIME COMMENT '发布时间',
                          `expire_time` DATETIME COMMENT '过期时间',
                          `create_by` BIGINT COMMENT '创建人ID',
                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                          `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- 用户已读公告记录表
CREATE TABLE `notice_read_record` (
                                      `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                      `notice_id` BIGINT NOT NULL COMMENT '公告ID',
                                      `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                      `read_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
                                      UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告已读记录表';

-- 用户消息表
CREATE TABLE `user_message` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `user_id` BIGINT NOT NULL COMMENT '接收者用户ID',
                                `sender_id` BIGINT DEFAULT NULL COMMENT '发送者用户ID',
                                `type` VARCHAR(20) NOT NULL COMMENT '消息类型：COMMENT,FOLLOW,ACTIVITY,NOTICE,SYSTEM',
                                `title` VARCHAR(100) DEFAULT NULL COMMENT '消息标题',
                                `content` TEXT NOT NULL COMMENT '消息内容',
                                `business_id` BIGINT DEFAULT NULL COMMENT '业务ID（活动ID/评论ID等）',
                                `link` VARCHAR(255) DEFAULT NULL COMMENT '跳转链接',
                                `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
                                `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
                                `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_user_id` (`user_id`),
                                KEY `idx_user_read` (`user_id`, `is_read`),
                                KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息记录表';

use pet_system;
ALTER TABLE user_message
    ADD COLUMN sign_status TINYINT DEFAULT 0 NOT NULL COMMENT '签到状态：0-未签到 1-已签到',
ADD COLUMN sign_time DATETIME NULL COMMENT '签到时间';

INSERT INTO `topic` (`name`, `description`, `post_count`, `view_count`, `hot_score`, `status`) VALUES
                                                                                                   ('#猫咪日常#', '分享你家猫咪的日常', 0, 0, 85.5, 1),
                                                                                                   ('#狗狗日常#', '狗狗的可爱瞬间', 0, 0, 78.2, 1),
                                                                                                   ('#领养日记#', '记录领养宠物的心路历程', 0, 0, 92.3, 1),
                                                                                                   ('#救助故事#', '救助流浪动物的故事', 0, 0, 88.6, 1),
                                                                                                   ('#宠物健康#', '宠物健康知识分享', 0, 0, 75.0, 1),
                                                                                                   ('#萌宠搞笑#', '宠物搞笑瞬间', 0, 0, 82.1, 1),
                                                                                                   ('#宠物美照#', '宠物的美照分享', 0, 0, 70.5, 1),
                                                                                                   ('#宠物用品#', '好用的宠物用品推荐', 0, 0, 65.0, 1),
                                                                                                   ('#宠物训练#', '宠物训练经验分享', 0, 0, 68.5, 1),
                                                                                                   ('#宠物旅行#', '带宠物去旅行', 0, 0, 60.0, 1);

INSERT INTO `daily_post` (`user_id`, `content`, `images`, `location`, `view_count`, `like_count`, `comment_count`, `share_count`, `status`, `audit_status`, `create_time`) VALUES
                                                                                                                                                                               (1, '今天带我家猫咪去洗澡了，超级乖！', '["https://picsum.photos/800/600?random=101"]', '杭州', 120, 45, 12, 8, 1, 1, '2026-04-20 10:00:00'),
                                                                                                                                                                               (2, '救助了一只流浪猫，带它去检查身体', '["https://picsum.photos/800/600?random=102"]', '北京', 89, 67, 23, 15, 1, 1, '2026-04-20 11:30:00'),
                                                                                                                                                                               (3, '领养的小橘猫已经3个月了，越来越粘人', '["https://picsum.photos/800/600?random=103"]', '上海', 234, 89, 34, 22, 1, 1, '2026-04-19 14:20:00'),
                                                                                                                                                                               (4, '路边发现受伤的小狗，已经送去医院了', '["https://picsum.photos/800/600?random=104"]', '广州', 567, 234, 78, 45, 1, 1, '2026-04-19 09:15:00'),
                                                                                                                                                                               (5, '我家金毛今天学会了握手！', '["https://picsum.photos/800/600?random=105"]', '深圳', 345, 123, 45, 28, 1, 1, '2026-04-18 16:45:00'),
                                                                                                                                                                               (1, '猫咪的日常：睡觉、吃饭、拆家', '["https://picsum.photos/800/600?random=106"]', '杭州', 78, 34, 8, 5, 1, 1, '2026-04-18 08:30:00'),
                                                                                                                                                                               (2, '推荐一款好用的宠物梳子', '["https://picsum.photos/800/600?random=107"]', '北京', 56, 23, 6, 12, 1, 1, '2026-04-17 19:00:00'),
                                                                                                                                                                               (3, '带狗狗去爬山，累并快乐着', '["https://picsum.photos/800/600?random=108"]', '杭州', 123, 56, 18, 9, 1, 1, '2026-04-17 13:20:00'),
                                                                                                                                                                               (4, '救助的小猫找到新家了！', '["https://picsum.photos/800/600?random=109"]', '上海', 456, 178, 56, 34, 1, 1, '2026-04-16 11:00:00'),
                                                                                                                                                                               (5, '宠物零食自制教程', '["https://picsum.photos/800/600?random=110"]', '深圳', 89, 45, 15, 23, 1, 1, '2026-04-16 09:30:00'),
                                                                                                                                                                               (1, '猫咪不喜欢新买的猫窝，气死我了', '["https://picsum.photos/800/600?random=111"]', '杭州', 34, 12, 5, 2, 1, 1, '2026-04-15 20:00:00'),
                                                                                                                                                                               (2, '宠物摄影技巧分享', '["https://picsum.photos/800/600?random=112"]', '北京', 67, 34, 9, 7, 1, 1, '2026-04-15 15:30:00'),
                                                                                                                                                                               (3, '带猫咪去体检，一切健康', '["https://picsum.photos/800/600?random=113"]', '上海', 45, 23, 7, 3, 1, 1, '2026-04-14 10:00:00'),
                                                                                                                                                                               (4, '流浪狗救助站志愿者招募', '["https://picsum.photos/800/600?random=114"]', '广州', 789, 345, 89, 67, 1, 1, '2026-04-14 08:00:00'),
                                                                                                                                                                               (5, '狗狗的饮食禁忌', '["https://picsum.photos/800/600?random=115"]', '深圳', 123, 56, 23, 12, 1, 1, '2026-04-13 17:00:00'),
                                                                                                                                                                               (1, '猫咪掉毛怎么办？求支招', '["https://picsum.photos/800/600?random=116"]', '杭州', 56, 23, 34, 5, 1, 1, '2026-04-13 12:30:00'),
                                                                                                                                                                               (2, '宠物保险有必要买吗？', '["https://picsum.photos/800/600?random=117"]', '北京', 45, 18, 12, 4, 1, 1, '2026-04-12 20:00:00'),
                                                                                                                                                                               (3, '领养代替购买，支持！', '["https://picsum.photos/800/600?random=118"]', '上海', 234, 98, 34, 23, 1, 1, '2026-04-12 14:00:00'),
                                                                                                                                                                               (4, '小区流浪猫绝育计划', '["https://picsum.photos/800/600?random=119"]', '广州', 345, 156, 45, 28, 1, 1, '2026-04-11 09:00:00'),
                                                                                                                                                                               (5, '宠物友好餐厅推荐', '["https://picsum.photos/800/600?random=120"]', '深圳', 78, 34, 11, 8, 1, 1, '2026-04-11 11:30:00');

INSERT INTO `daily_topic_rel` (`daily_id`, `topic_id`) VALUES
                                                           (1, 1), (1, 6),
                                                           (2, 4), (2, 9),
                                                           (3, 3), (3, 1),
                                                           (4, 4), (4, 9),
                                                           (5, 2), (5, 6),
                                                           (6, 1), (6, 6),
                                                           (7, 8), (7, 5),
                                                           (8, 2), (8, 10),
                                                           (9, 3), (9, 4),
                                                           (10, 5), (10, 8),
                                                           (11, 1), (11, 6),
                                                           (12, 5), (12, 8),
                                                           (13, 1), (13, 5),
                                                           (14, 4), (14, 9),
                                                           (15, 2), (15, 5),
                                                           (16, 1), (16, 5),
                                                           (17, 8), (17, 5),
                                                           (18, 3), (18, 4),
                                                           (19, 4), (19, 9),
                                                           (20, 2), (20, 10);

INSERT INTO `daily_user_behavior` (`user_id`, `target_id`, `action_type`, `action_time`) VALUES
-- 用户1喜欢猫相关内容
(1, 1, 'view', '2026-04-22 10:00:00'),
(1, 1, 'like', '2026-04-22 10:00:05'),
(1, 3, 'view', '2026-04-22 11:00:00'),
(1, 3, 'like', '2026-04-22 11:00:10'),
(1, 6, 'view', '2026-04-22 12:00:00'),
(1, 6, 'share', '2026-04-22 12:00:20'),
(1, 11, 'view', '2026-04-22 13:00:00'),
(1, 13, 'view', '2026-04-22 14:00:00'),
(1, 13, 'like', '2026-04-22 14:00:15'),
(1, 16, 'view', '2026-04-22 15:00:00'),

-- 用户2喜欢救助相关内容
(2, 2, 'view', '2026-04-22 10:30:00'),
(2, 2, 'like', '2026-04-22 10:30:10'),
(2, 2, 'share', '2026-04-22 10:30:20'),
(2, 4, 'view', '2026-04-22 11:30:00'),
(2, 4, 'like', '2026-04-22 11:30:05'),
(2, 9, 'view', '2026-04-22 12:30:00'),
(2, 14, 'view', '2026-04-22 13:30:00'),
(2, 14, 'like', '2026-04-22 13:30:15'),
(2, 14, 'share', '2026-04-22 13:30:25'),
(2, 19, 'view', '2026-04-22 14:30:00'),

-- 用户3喜欢狗狗相关内容
(3, 5, 'view', '2026-04-22 09:00:00'),
(3, 5, 'like', '2026-04-22 09:00:10'),
(3, 8, 'view', '2026-04-22 10:00:00'),
(3, 8, 'like', '2026-04-22 10:00:05'),
(3, 15, 'view', '2026-04-22 11:00:00'),
(3, 15, 'like', '2026-04-22 11:00:15'),
(3, 20, 'view', '2026-04-22 12:00:00'),
(3, 20, 'share', '2026-04-22 12:00:10'),

-- 用户4喜欢领养相关内容
(4, 3, 'view', '2026-04-22 08:00:00'),
(4, 3, 'like', '2026-04-22 08:00:10'),
(4, 3, 'share', '2026-04-22 08:00:20'),
(4, 9, 'view', '2026-04-22 09:00:00'),
(4, 9, 'like', '2026-04-22 09:00:05'),
(4, 18, 'view', '2026-04-22 10:00:00'),
(4, 18, 'like', '2026-04-22 10:00:15'),

-- 用户5喜欢宠物用品相关内容
(5, 7, 'view', '2026-04-22 15:00:00'),
(5, 7, 'like', '2026-04-22 15:00:10'),
(5, 10, 'view', '2026-04-22 16:00:00'),
(5, 10, 'share', '2026-04-22 16:00:20'),
(5, 12, 'view', '2026-04-22 17:00:00'),
(5, 12, 'like', '2026-04-22 17:00:05'),
(5, 17, 'view', '2026-04-22 18:00:00');

INSERT INTO `follow` (`follower_id`, `following_id`, `create_time`) VALUES
                                                                        (1, 2, '2026-04-20 10:00:00'),
                                                                        (1, 3, '2026-04-20 10:00:00'),
                                                                        (2, 4, '2026-04-20 10:00:00'),
                                                                        (3, 1, '2026-04-20 10:00:00'),
                                                                        (3, 5, '2026-04-20 10:00:00'),
                                                                        (4, 2, '2026-04-20 10:00:00'),
                                                                        (4, 3, '2026-04-20 10:00:00'),
                                                                        (5, 1, '2026-04-20 10:00:00'),
                                                                        (5, 2, '2026-04-20 10:00:00');

INSERT INTO `like_record` (`user_id`, `target_id`, `target_type`, `create_time`) VALUES
                                                                                     (1, 1, 'daily', '2026-04-22 10:00:05'),
                                                                                     (1, 6, 'daily', '2026-04-22 12:00:20'),
                                                                                     (1, 13, 'daily', '2026-04-22 14:00:15'),
                                                                                     (2, 2, 'daily', '2026-04-22 10:30:10'),
                                                                                     (2, 4, 'daily', '2026-04-22 11:30:05'),
                                                                                     (2, 14, 'daily', '2026-04-22 13:30:15'),
                                                                                     (3, 5, 'daily', '2026-04-22 09:00:10'),
                                                                                     (3, 8, 'daily', '2026-04-22 10:00:05'),
                                                                                     (3, 15, 'daily', '2026-04-22 11:00:15'),
                                                                                     (4, 3, 'daily', '2026-04-22 08:00:10'),
                                                                                     (4, 9, 'daily', '2026-04-22 09:00:05'),
                                                                                     (4, 18, 'daily', '2026-04-22 10:00:15'),
                                                                                     (5, 7, 'daily', '2026-04-22 15:00:10'),
                                                                                     (5, 12, 'daily', '2026-04-22 17:00:05');

-- 根据点赞记录，重新计算并更新每个帖子的点赞数
UPDATE daily_post d
SET like_count = (
    SELECT COUNT(*)
    FROM like_record l
    WHERE l.target_id = d.id
      AND l.target_type = 'daily'
);

-- 根据关注关系，重新计算并更新每个用户的粉丝数和关注数
UPDATE user u
SET follower_count = (
    SELECT COUNT(*)
    FROM follow f
    WHERE f.following_id = u.id
);

UPDATE user u
SET following_count = (
    SELECT COUNT(*)
    FROM follow f
    WHERE f.follower_id = u.id
);