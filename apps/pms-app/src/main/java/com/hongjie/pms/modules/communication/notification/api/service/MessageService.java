package com.hongjie.pms.modules.communication.notification.api.service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.communication.notification.infrastructure.persistence.entity.UserMessage;
import java.util.List;

/** Runtime contract implemented by the migrated service. */
public interface MessageService {
    void sendAndPush(UserMessage message);
    void sendAndPushBatch(List<UserMessage> messages);
    void sendLikeNotification(Long targetUserId, Long senderId,
                                     String targetType, String targetTitle,
                                     Long businessId, String link);
    void sendCommentNotification(Long targetUserId, Long senderId,
                                        String targetType, String targetTitle,
                                        String commentContent,
                                        Long businessId, String link);
    void sendFollowNotification(Long targetUserId, Long senderId,
                                       String senderName, String link);
    void sendSignUpSuccessNotification(Long userId, String activityTitle,
                                              Long activityId);
    void sendSomeoneSignUpNotification(Long publisherId, Long signUpUserId,
                                              String userName, String activityTitle,
                                              Long activityId);
    void sendSignInSuccessNotification(Long userId, String activityTitle,
                                              Long activityId);
    void sendActivityReminder(Long userId, String activityTitle,
                                     Long activityId, int minutes);
    void sendActivityFullNotification(Long publisherId, String activityTitle,
                                             Long activityId);
    void sendAuditPassNotification(Long userId, String postTitle,
                                          Long postId, String postType);
    void sendAuditRejectNotification(Long userId, String postTitle,
                                            Long postId, String postType, String reason);
    void sendPunishmentStartNotification(Long userId, String content);
    void sendPunishmentEndNotification(Long userId);
    Long getUnreadCount(Long userId);
    IPage<UserMessage> getMessageList(Long userId, Integer pageNum,
                                             Integer pageSize, String type);
    void markAsRead(Long messageId);
    void markAllAsRead(String type);
    void sendActivityStatisticsNotification(Long userId, String activityTitle,
                                                   Long activityId, long totalSignups,
                                                   long signedCount, long noShowCount);
}
