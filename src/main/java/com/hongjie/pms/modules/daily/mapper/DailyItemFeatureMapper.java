package com.hongjie.pms.modules.daily.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.daily.entity.DailyItemFeature;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DailyItemFeatureMapper extends BaseMapper<DailyItemFeature> {

    @Select("SELECT * FROM daily_item_feature ORDER BY hot_score DESC LIMIT #{limit}")
    List<DailyItemFeature> getHotFeatures(@Param("limit") int limit);
}