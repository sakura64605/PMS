package com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.FavoriteRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteRecordMapper extends BaseMapper<FavoriteRecord> {
}
