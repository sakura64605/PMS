package com.hongjie.pms.modules.community.feed.application;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface FeedWriteMapper {
    @Select("SELECT id FROM user WHERE id=#{author} FOR UPDATE")
    Long lockAuthor(@Param("author") Long author);
    @Select("SELECT COUNT(*) FROM follow WHERE following_id=#{author}")
    int fans(@Param("author") Long author);
    @Insert("INSERT INTO big_v_config(user_id,fans_count,use_pull_mode,update_time) VALUES(#{author},#{fans},#{mode},NOW()) ON DUPLICATE KEY UPDATE fans_count=VALUES(fans_count),use_pull_mode=VALUES(use_pull_mode),update_time=NOW()")
    int mode(@Param("author") Long author,@Param("fans") int fans,@Param("mode") int mode);

    @Insert({"<script>",
            "INSERT INTO user_inbox(user_id,post_id,post_type,poster_id,poster_name,poster_avatar,title,cover_image,create_time,is_read)",
            "SELECT f.follower_id,#{post},#{type},#{author},#{name},#{avatar},#{title},#{cover},#{time},0 FROM follow f WHERE f.following_id=#{author}",
            "AND NOT EXISTS (SELECT 1 FROM user_inbox i WHERE i.user_id=f.follower_id AND i.poster_id=#{author} AND i.post_id=#{post} AND LOWER(i.post_type) IN",
            "<choose><when test='type == &quot;pet&quot;'>"+FeedSql.PET_TYPES+"</when><otherwise>"+FeedSql.ACTIVITY_TYPES+"</otherwise></choose>)",
            "</script>"})
    int fanOut(@Param("author") Long author,@Param("post") Long post,@Param("type") String type,
               @Param("name") String name,@Param("avatar") String avatar,@Param("title") String title,
               @Param("cover") String cover,@Param("time") LocalDateTime time);
}
