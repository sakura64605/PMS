package com.hongjie.pms.modules.communication.privatemessage.application;

public final class PrivateMessageSql {
    private PrivateMessageSql() {}
    static final String VISIBLE="((m.from_user_id=#{actor} AND m.is_deleted_by_from=0) OR (m.to_user_id=#{actor} AND m.is_deleted_by_to=0))";
    static final String VIEW="SELECT c.id,c.user_a,c.user_b,"
            +"(SELECT LEFT(m.content,500) FROM private_message m WHERE m.conversation_id=c.id AND "+VISIBLE+" ORDER BY m.create_time DESC,m.id DESC LIMIT 1) last_message,"
            +"(SELECT m.create_time FROM private_message m WHERE m.conversation_id=c.id AND "+VISIBLE+" ORDER BY m.create_time DESC,m.id DESC LIMIT 1) last_message_time,"
            +"(SELECT COUNT(*) FROM private_message m WHERE m.conversation_id=c.id AND m.to_user_id=#{actor} AND m.is_read=0 AND m.is_deleted_by_to=0) unread_count "
            +"FROM private_conversation c ";
}
