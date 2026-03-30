package com.hongjie.pms.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {

    @Update("UPDATE activity SET like_count = like_count + 1 WHERE id = #{petId}")
    void incrementLikeCount(@Param("petId") Long petId);

    @Update("UPDATE activity SET like_count = like_count - 1 WHERE id = #{petId} AND like_count > 0")
    void decrementLikeCount(@Param("petId") Long petId);

}
