package com.hongjie.pms.modules.like.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LikeRecordMapper extends BaseMapper<LikeRecord> {

    /**
     * 批量查询用户对多个目标的点赞记录
     * @param userId 当前用户ID
     * @param targetIds 目标ID列表
     * @param targetType 目标类型
     * @return 点赞记录列表
     */
    @Select({
            "<script>",
            "SELECT * FROM like_record WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id IN",
            "<foreach collection='targetIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<LikeRecord> selectByUserAndTargetIds(@Param("userId") Long userId,
                                               @Param("targetIds") List<Long> targetIds,
                                               @Param("targetType") String targetType);
}
