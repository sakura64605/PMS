package com.hongjie.pms.modules.community.feed.application;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface FeedReadMapper {
    @SelectProvider(type=FeedSql.class,method="page")
    List<FeedKey> page(@Param("actor") Long actor,@Param("offset") long offset,@Param("size") long size);
    @SelectProvider(type=FeedSql.class,method="count")
    long total(@Param("actor") Long actor);
    @SelectProvider(type=FeedSql.class,method="unread")
    long unread(@Param("actor") Long actor);

    @Update({"<script>","UPDATE user_inbox SET is_read=1,read_time=NOW() WHERE user_id=#{actor} AND is_read=0 AND (",
            "<foreach collection='keys' item='key' separator=' OR '>",
            "(post_id=#{key.postId} AND poster_id=#{key.posterId} AND LOWER(post_type) IN",
            "<choose><when test='key.postType == &quot;pet&quot;'>"+FeedSql.PET_TYPES+"</when><otherwise>"+FeedSql.ACTIVITY_TYPES+"</otherwise></choose>)",
            "</foreach>",")","</script>"})
    int markRead(@Param("actor") Long actor,@Param("keys") List<FeedKey> keys);
}
