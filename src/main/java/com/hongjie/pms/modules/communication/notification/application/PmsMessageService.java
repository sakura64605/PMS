package com.hongjie.pms.modules.communication.notification.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.communication.notification.infrastructure.persistence.entity.UserMessage;
import com.hongjie.pms.modules.communication.notification.api.service.MessageService;
import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.extension.notify.*;
import com.hongjie.pms.shared.extension.notify.handler.*;
import com.hongjie.pms.shared.extension.target.TargetTypeCodec;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/** Compatibility adapter for existing concrete-type injections. Every public legacy operation is overridden. */
@Service
@Primary
public class PmsMessageService implements MessageService {
    private final MessageInbox inbox;
    private final MessageDelivery delivery;
    private final LikeNotificationHandler likes;
    private final CommentNotificationHandler comments;
    private final TargetTypeCodec codec;
    public PmsMessageService(MessageInbox inbox, MessageDelivery delivery, LikeNotificationHandler likes, CommentNotificationHandler comments, TargetTypeCodec codec){
        this.inbox=inbox;this.delivery=delivery;this.likes=likes;this.comments=comments;this.codec=codec;
    }
    @Override public Long getUnreadCount(Long actor){return inbox.unread(actor);}
    @Override public IPage<UserMessage> getMessageList(Long actor,Integer number,Integer size,String type){return inbox.list(actor,number,size,type);}
    @Override public void markAsRead(Long id){inbox.read(id);}
    @Override public void markAllAsRead(String type){inbox.readAll(type);}
    @Override public void sendAndPush(UserMessage message){delivery.enqueue(delivery.snapshot(message));}
    @Override public void sendAndPushBatch(List<UserMessage> messages){
        var snapshots=messages.stream().map(delivery::snapshot).toList();snapshots.forEach(delivery::validate);snapshots.forEach(delivery::enqueue);
    }
    private void emit(Long user,Long sender,String type,String title,String content,Long id,String link){delivery.enqueue(new PendingNotification(user,sender,type,title,content,id,link,LocalDateTime.now()));}
    private void emit(NotificationMessage message){emit(message.userId(),message.senderId(),message.type().getCode(),message.title(),message.content(),message.businessId(),message.link());}
    private BizTargetType target(String type){return codec.tryRead(type).orElse(BizTargetType.USER);}
    @Override public void sendLikeNotification(Long user,Long sender,String type,String title,Long id,String link){
        if("系统通知".equals(type)){emit(user,sender,"LIKE","系统通知","系统通知:注意",id,link);return;}
        emit(likes.build(new LikeNotificationContext(user,sender,target(type),title,id,link)));
    }
    @Override public void sendCommentNotification(Long user,Long sender,String type,String title,String content,Long id,String link){emit(comments.build(new CommentNotificationContext(user,sender,target(type),title,content,id,link)));}
    @Override public void sendFollowNotification(Long user,Long sender,String name,String link){emit(user,sender,"FOLLOW","新粉丝",name+" 关注了你",null,link);}
    @Override public void sendSignUpSuccessNotification(Long user,String title,Long id){emit(user,null,"SIGN_UP","报名成功","你已成功报名活动《"+title+"》",id,"/activity/"+id);}
    @Override public void sendSomeoneSignUpNotification(Long user,Long sender,String name,String title,Long id){emit(user,sender,"SIGN_UP","新报名",name+" 报名了你的活动《"+title+"》",id,"/activity/signup-list/"+id);}
    @Override public void sendSignInSuccessNotification(Long user,String title,Long id){emit(user,null,"SIGN_IN","签到成功","你在活动《"+title+"》中签到成功",id,"/activity/"+id);}
    @Override public void sendActivityReminder(Long user,String title,Long id,int minutes){emit(user,null,"ACTIVITY_REMINDER","活动提醒","你报名的活动《"+title+"》即将在 "+minutes+" 分钟后开始",id,"/activity/"+id);}
    @Override public void sendActivityFullNotification(Long user,String title,Long id){emit(user,null,"ACTIVITY_FULL","活动已满员","你的活动《"+title+"》已报满",id,"/activity/"+id);}
    @Override public void sendAuditPassNotification(Long user,String title,Long id,String type){emit(user,null,"AUDIT_PASS","审核通过","你的"+type+"《"+title+"》已通过审核",id,"/"+type+"/"+id);}
    @Override public void sendAuditRejectNotification(Long user,String title,Long id,String type,String reason){emit(user,null,"AUDIT_REJECT","审核未通过","你的"+type+"《"+title+"》未通过审核"+(reason==null||reason.isEmpty()?"":"，原因："+reason),id,"/"+type+"/edit/"+id);}
    @Override public void sendPunishmentStartNotification(Long user,String content){emit(user,null,"PUNISHMENT","账号受限",content,null,null);}
    @Override public void sendPunishmentEndNotification(Long user){emit(user,null,"PUNISHMENT","惩罚期结束","您的惩罚期已结束，可以正常报名活动了",null,null);}
    @Override public void sendActivityStatisticsNotification(Long user,String title,Long id,long total,long signed,long absent){emit(user,null,"ACTIVITY_STATISTICS","活动统计报告",String.format("您的活动《%s》已结束。报名人数：%d，签到人数：%d，爽约人数：%d",title,total,signed,absent),id,"/activity/"+id+"/statistics");}
}
