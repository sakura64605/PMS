package com.hongjie.pms.modules.petpost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PetPostMapper extends BaseMapper<PetPost> {

    @Update("UPDATE pet_post SET like_count = like_count + 1 WHERE id = #{petId}")
    void incrementLikeCount(@Param("petId") Long petId);

    @Update("UPDATE pet_post SET like_count = like_count - 1 WHERE id = #{petId} AND like_count > 0")
    void decrementLikeCount(@Param("petId") Long petId);

    @Update("UPDATE pet_post SET comment_count = comment_count + 1 WHERE id = #{petId}")
    void incrementCommentCount(@Param("petId") Long petId);

    @Update("UPDATE pet_post SET comment_count = comment_count - 1 WHERE id = #{petId} AND comment_count > 0")
    void decrementCommentCount(@Param("petId") Long petId);

}
