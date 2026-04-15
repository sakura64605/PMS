package com.hongjie.pms.modules.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.CommentLikeTypes;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.message.entity.UserMessage;
import com.hongjie.pms.modules.message.mapper.UserMessageMapper;
import com.hongjie.pms.modules.message.mq.MessageMqDto;
import com.hongjie.pms.modules.message.mq.MessageMqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final UserMessageMapper messageMapper;
    private final MessageMqProducer messageMqProducer;

    // ==================== 核心方法 ====================

//    /**
//     * 发送消息（存储 + 实时推送）
//     */
//    public void sendAndPush(UserMessage message) {
//        // 1. 存储到数据库
//        message.setIsRead(0);
//        message.setCreateTime(LocalDateTime.now());
//        messageMapper.insert(message);
//        log.info("消息已存储: userId={}, type={}", message.getUserId(), message.getType());
//
//        // 2. 实时推送（WebSocket）
//        WebSocketHandler.pushToUser(message.getUserId(), message);
//    }

    // 修改 sendAndPush 方法
    public void sendAndPush(UserMessage message) {
        // 转换为 DTO
        MessageMqDto dto = MessageMqDto.builder()
                .userId(message.getUserId())
                .senderId(message.getSenderId())
                .type(message.getType())
                .title(message.getTitle())
                .content(message.getContent())
                .businessId(message.getBusinessId())
                .link(message.getLink())
                .createTime(LocalDateTime.now())
                .build();
        // 发送到 MQ，消费者会处理存库和推送
        messageMqProducer.send(dto);
    }

    /**
     * 批量发送消息
     */
    public void sendAndPushBatch(List<UserMessage> messages) {
        for (UserMessage message : messages) {
            sendAndPush(message);
        }
    }

    // ==================== 便捷方法（基于现有表字段）====================

    /**
     * 发送点赞通知（统一入口）
     */
    public void sendLikeNotification(Long targetUserId, Long senderId,
                                     String targetType, String targetTitle,
                                     Long businessId, String link) {
        String title = getLikeTitle(targetType);
        String content = getLikeContent(targetType, targetTitle);

        UserMessage message = new UserMessage();
        message.setUserId(targetUserId);
        message.setSenderId(senderId);
        message.setType("LIKE");
        message.setTitle(title);
        message.setContent(content);
        message.setBusinessId(businessId);
        message.setLink(link);
        sendAndPush(message);
    }

    /**
     * 根据目标类型获取标题
     */
    private String getLikeTitle(String targetType) {
        switch (targetType) {
            case "pet_post":
                return "有人点赞了你的帖子";
            case "pet_comment":
                return "有人点赞了你的评论";
            case "pet_activity":
                return "有人点赞了你的活动";
            default:
                return "有人点赞了";
        }
    }

    /**
     * 根据目标类型获取内容
     */
    private String getLikeContent(String targetType, String targetTitle) {
        switch (targetType) {
            case "pet_post":
                return "点赞了你的帖子《" + targetTitle + "》";
            case "pet_comment":
                return "点赞了你的评论";
            case "pet_activity":
                return "点赞了你的活动《" + targetTitle + "》";
            default:
                return "点赞了《" + targetTitle + "》";
        }
    }

    /**
     * 发送评论通知
     */
    public void sendCommentNotification(Long targetUserId, Long senderId,
                                        String targetType, String targetTitle,
                                        String commentContent,
                                        Long businessId, String link) {
        String title = getCommentTitle(targetType);
        String content = getCommentContent(targetType, targetTitle, commentContent);

        // 内容过长时截断
        if (content.length() > 100) {
            content = content.substring(0, 100) + "...";
        }

        UserMessage message = new UserMessage();
        message.setUserId(targetUserId);
        message.setSenderId(senderId);
        message.setType("COMMENT");
        message.setTitle(title);
        message.setContent(content);
        message.setBusinessId(businessId);
        message.setLink(link);
        sendAndPush(message);
    }

    private String getCommentTitle(String targetType) {
        switch (targetType) {
            case CommentLikeTypes.PET_POST:
                return "有人评论了你的帖子";
            case CommentLikeTypes.PET_ACTIVITY:
                return "有人评论了你的活动";
            default:
                return "有人评论了你";
        }
    }

    private String getCommentContent(String targetType, String targetTitle, String commentContent) {
        switch (targetType) {
            case CommentLikeTypes.PET_POST:
                return "评论了你的帖子《" + targetTitle + "》：" + commentContent;
            case CommentLikeTypes.PET_ACTIVITY:
                return "评论了你的活动《" + targetTitle + "》：" + commentContent;
            default:
                return commentContent;
        }
    }

    /**
     * 发送关注通知
     */
    public void sendFollowNotification(Long targetUserId, Long senderId,
                                       String senderName, String link) {
        UserMessage message = new UserMessage();
        message.setUserId(targetUserId);
        message.setSenderId(senderId);
        message.setType("FOLLOW");
        message.setTitle("新粉丝");
        message.setContent(senderName + " 关注了你");
        message.setLink(link);
        sendAndPush(message);
    }

    /**
     * 发送报名成功通知
     */
    public void sendSignUpSuccessNotification(Long userId, String activityTitle,
                                              Long activityId) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);  // 系统消息
        message.setType("SIGN_UP");
        message.setTitle("报名成功");
        message.setContent("你已成功报名活动《" + activityTitle + "》");
        message.setBusinessId(activityId);
        message.setLink("/activity/" + activityId);
        sendAndPush(message);
    }

    /**
     * 发送有人报名通知（给活动发布者）
     */
    public void sendSomeoneSignUpNotification(Long publisherId, Long signUpUserId,
                                              String userName, String activityTitle,
                                              Long activityId) {
        UserMessage message = new UserMessage();
        message.setUserId(publisherId);
        message.setSenderId(signUpUserId);
        message.setType("SIGN_UP");
        message.setTitle("新报名");
        message.setContent(userName + " 报名了你的活动《" + activityTitle + "》");
        message.setBusinessId(activityId);
        message.setLink("/activity/signup-list/" + activityId);
        sendAndPush(message);
    }

    /**
     * 发送签到成功通知
     */
    public void sendSignInSuccessNotification(Long userId, String activityTitle,
                                              Long activityId) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);
        message.setType("SIGN_IN");
        message.setTitle("签到成功");
        message.setContent("你在活动《" + activityTitle + "》中签到成功");
        message.setBusinessId(activityId);
        message.setLink("/activity/" + activityId);
        sendAndPush(message);
    }

    /**
     * 发送活动提醒
     */
    public void sendActivityReminder(Long userId, String activityTitle,
                                     Long activityId, int minutes) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);
        message.setType("ACTIVITY_REMINDER");
        message.setTitle("活动提醒");
        message.setContent("你报名的活动《" + activityTitle + "》即将在 " + minutes + " 分钟后开始");
        message.setBusinessId(activityId);
        message.setLink("/activity/" + activityId);
        sendAndPush(message);
    }

    /**
     * 发送活动满员通知
     */
    public void sendActivityFullNotification(Long publisherId, String activityTitle,
                                             Long activityId) {
        UserMessage message = new UserMessage();
        message.setUserId(publisherId);
        message.setSenderId(null);
        message.setType("ACTIVITY_FULL");
        message.setTitle("活动已满员");
        message.setContent("你的活动《" + activityTitle + "》已报满");
        message.setBusinessId(activityId);
        message.setLink("/activity/" + activityId);
        sendAndPush(message);
    }

    /**
     * 发送审核通过通知
     */
    public void sendAuditPassNotification(Long userId, String postTitle,
                                          Long postId, String postType) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);
        message.setType("AUDIT_PASS");
        message.setTitle("审核通过");
        message.setContent("你的" + postType + "《" + postTitle + "》已通过审核");
        message.setBusinessId(postId);
        message.setLink("/" + postType + "/" + postId);
        sendAndPush(message);
    }

    /**
     * 发送审核拒绝通知
     */
    public void sendAuditRejectNotification(Long userId, String postTitle,
                                            Long postId, String postType, String reason) {
        String content = "你的" + postType + "《" + postTitle + "》未通过审核";
        if (reason != null && !reason.isEmpty()) {
            content += "，原因：" + reason;
        }

        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);
        message.setType("AUDIT_REJECT");
        message.setTitle("审核未通过");
        message.setContent(content);
        message.setBusinessId(postId);
        message.setLink("/" + postType + "/edit/" + postId);
        sendAndPush(message);
    }

    /**
     * 发送惩罚开始通知
     */
    public void sendPunishmentStartNotification(Long userId, int days) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);
        message.setType("PUNISHMENT");
        message.setTitle("账号受限");
        message.setContent("您因爽约率过高，已被禁止报名 " + days + " 天");
        sendAndPush(message);
    }

    /**
     * 发送惩罚结束通知
     */
    public void sendPunishmentEndNotification(Long userId) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);
        message.setType("PUNISHMENT");
        message.setTitle("惩罚期结束");
        message.setContent("您的惩罚期已结束，可以正常报名活动了");
        sendAndPush(message);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取未读消息数量
     */
    public Long getUnreadCount(Long userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0);
        return messageMapper.selectCount(wrapper);
    }

    /**
     * 获取消息列表
     */
    public IPage<UserMessage> getMessageList(Long userId, Integer pageNum,
                                             Integer pageSize, String type) {
        Page<UserMessage> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .orderByDesc(UserMessage::getCreateTime);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(UserMessage::getType, type);
        }

        return messageMapper.selectPage(page, wrapper);
    }

    /**
     * 标记为已读
     */
    public void markAsRead(Long messageId) {
        UserMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (!message.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        message.setIsRead(1);
        message.setReadTime(LocalDateTime.now());
        messageMapper.updateById(message);
    }

    /**
     * 全部标记为已读
     */
    public void markAllAsRead(String type) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(UserMessage::getType, type);
        }

        UserMessage update = new UserMessage();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.update(update, wrapper);
    }

    /**
     * 发送活动统计报告通知
     *
     * @param userId 活动发布者ID
     * @param activityTitle 活动标题
     * @param activityId 活动ID
     * @param totalSignups 总报名人数
     * @param signedCount 签到人数
     * @param noShowCount 爽约人数
     */
    public void sendActivityStatisticsNotification(Long userId, String activityTitle,
                                                   Long activityId, long totalSignups,
                                                   long signedCount, long noShowCount) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setSenderId(null);  // 系统消息
        message.setType("ACTIVITY_STATISTICS");
        message.setTitle("活动统计报告");
        message.setContent(String.format("您的活动《%s》已结束。报名人数：%d，签到人数：%d，爽约人数：%d",
                activityTitle, totalSignups, signedCount, noShowCount));
        message.setBusinessId(activityId);
        message.setLink("/activity/" + activityId + "/statistics");

        sendAndPush(message);
    }
}