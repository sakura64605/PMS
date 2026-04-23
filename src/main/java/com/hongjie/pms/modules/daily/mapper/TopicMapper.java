package com.hongjie.pms.modules.daily.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.daily.entity.Topic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TopicMapper extends BaseMapper<Topic> {

    /**
     * 获取热门话题
     */
    @Select("SELECT * FROM topic WHERE status = 1 ORDER BY hot_score DESC LIMIT #{limit}")
    List<Topic> getHotTopics(@Param("limit") int limit);

    /**
     * 搜索话题
     */
    @Select("SELECT * FROM topic WHERE status = 1 AND name LIKE CONCAT('%', #{keyword}, '%') LIMIT #{limit}")
    List<Topic> searchTopics(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 增加帖子数
     */
    @Update("UPDATE topic SET post_count = post_count + 1 WHERE id = #{id}")
    void incrementPostCount(@Param("id") Long id);

    /**
     * 增加浏览量
     */
    @Update("UPDATE topic SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);

    /**
     * 更新热度分
     */
    @Update("UPDATE topic SET hot_score = #{hotScore} WHERE id = #{id}")
    void updateHotScore(@Param("id") Long id, @Param("hotScore") Double hotScore);
}