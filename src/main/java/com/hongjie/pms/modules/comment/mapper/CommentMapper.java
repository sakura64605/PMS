package com.hongjie.pms.modules.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.comment.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Update("UPDATE comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    void incrementLikeCount(@Param("commentId") Long commentId);

    @Update("UPDATE comment SET like_count = like_count - 1 WHERE id = #{commentId} AND like_count > 0")
    void decrementLikeCount(@Param("commentId") Long commentId);

}
