package com.hongjie.pms.modules.communication.privatemessage.application;

import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.entity.PrivateConversation;
import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.entity.PrivateMessage;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PrivateMessageWriteMapper {
    @Select("SELECT * FROM private_message WHERE from_user_id=#{actor} AND client_message_id=#{key} FOR UPDATE")
    PrivateMessage byClientMessageId(@Param("actor") Long actor, @Param("key") String key);
    @Select("SELECT id,status FROM user WHERE id=#{id} FOR UPDATE") User lockUser(@Param("id") Long id);
    @Select("SELECT * FROM private_conversation WHERE user_a=#{low} AND user_b=#{high} FOR UPDATE")
    PrivateConversation pair(@Param("low") Long low,@Param("high") Long high);
    @Select("SELECT * FROM private_conversation WHERE id=#{id} FOR UPDATE") PrivateConversation lock(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM private_conversation WHERE user_a=#{actor} OR user_b=#{actor}") long conversationCount(@Param("actor") Long actor);
    @Select(PrivateMessageSql.VIEW+"WHERE c.user_a=#{actor} OR c.user_b=#{actor} ORDER BY last_message_time DESC,c.id DESC LIMIT #{size} OFFSET #{offset}")
    List<ConversationView> conversations(@Param("actor") Long actor,@Param("offset") long offset,@Param("size") long size);
    @Select(PrivateMessageSql.VIEW+"WHERE c.id=#{id} AND (c.user_a=#{actor} OR c.user_b=#{actor})") ConversationView view(@Param("actor") Long actor,@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM private_message m WHERE m.conversation_id=#{id} AND "+PrivateMessageSql.VISIBLE)
    long messageCount(@Param("actor") Long actor,@Param("id") Long id);
    @Select("SELECT m.* FROM private_message m WHERE m.conversation_id=#{id} AND "+PrivateMessageSql.VISIBLE+" ORDER BY m.create_time ASC,m.id ASC LIMIT #{size} OFFSET #{offset}")
    List<PrivateMessage> messages(@Param("actor") Long actor,@Param("id") Long id,@Param("offset") long offset,@Param("size") long size);
    @Select("<script>SELECT m.* FROM private_message m WHERE m.conversation_id=#{id} AND "+PrivateMessageSql.VISIBLE+
            "<if test='before != null'> AND m.id &lt; #{before}</if> ORDER BY m.id DESC LIMIT #{size}</script>")
    List<PrivateMessage> olderMessages(@Param("actor") Long actor, @Param("id") Long id, @Param("before") Long before, @Param("size") int size);
    @Select("SELECT m.* FROM private_message m WHERE m.conversation_id=#{id} AND "+PrivateMessageSql.VISIBLE+" AND m.id > #{after} ORDER BY m.id ASC LIMIT #{size}")
    List<PrivateMessage> newerMessages(@Param("actor") Long actor, @Param("id") Long id, @Param("after") Long after, @Param("size") int size);
    @Select("SELECT COUNT(*) FROM private_message m JOIN private_conversation c ON c.id=m.conversation_id WHERE m.to_user_id=#{actor} AND m.is_read=0 AND m.is_deleted_by_to=0 AND (c.user_a=#{actor} OR c.user_b=#{actor})")
    long unread(@Param("actor") Long actor);
    @Update("UPDATE private_message SET is_read=1 WHERE conversation_id=#{id} AND to_user_id=#{actor} AND is_read=0 AND is_deleted_by_to=0")
    int markRead(@Param("actor") Long actor,@Param("id") Long id);
    @Update("UPDATE private_conversation SET unread_count_a=CASE WHEN user_a=#{actor} THEN 0 ELSE unread_count_a END,unread_count_b=CASE WHEN user_b=#{actor} THEN 0 ELSE unread_count_b END WHERE id=#{id} AND (user_a=#{actor} OR user_b=#{actor})")
    int clearUnread(@Param("actor") Long actor,@Param("id") Long id);
    @Update("UPDATE private_conversation SET last_message=#{preview},last_message_time=#{time},unread_count_a=COALESCE(unread_count_a,0)+CASE WHEN user_a=#{recipient} THEN 1 ELSE 0 END,unread_count_b=COALESCE(unread_count_b,0)+CASE WHEN user_b=#{recipient} THEN 1 ELSE 0 END WHERE id=#{id}")
    int sent(@Param("id") Long id,@Param("recipient") Long recipient,@Param("preview") String preview,@Param("time") LocalDateTime time);
    @Update("UPDATE private_message SET is_deleted_by_from=CASE WHEN from_user_id=#{actor} THEN 1 ELSE is_deleted_by_from END,is_deleted_by_to=CASE WHEN to_user_id=#{actor} THEN 1 ELSE is_deleted_by_to END,is_read=CASE WHEN to_user_id=#{actor} THEN 1 ELSE is_read END WHERE conversation_id=#{id} AND (from_user_id=#{actor} OR to_user_id=#{actor})")
    int hide(@Param("actor") Long actor,@Param("id") Long id);
}
