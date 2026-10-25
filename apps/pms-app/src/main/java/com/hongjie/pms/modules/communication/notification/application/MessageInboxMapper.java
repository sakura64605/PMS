package com.hongjie.pms.modules.communication.notification.application;

import org.apache.ibatis.annotations.*;

@Mapper
public interface MessageInboxMapper {
    @Update("UPDATE user_message SET is_read=1,read_time=NOW() WHERE id=#{id} AND user_id=#{actor} AND is_read=0")
    int readOne(@Param("actor") Long actor,@Param("id") Long id);
    @Update({"<script>","UPDATE user_message SET is_read=1,read_time=NOW() WHERE user_id=#{actor} AND is_read=0",
            "<if test='type != null'> AND type=#{type}</if>","</script>"})
    int readAll(@Param("actor") Long actor,@Param("type") String type);
}
