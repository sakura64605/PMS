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