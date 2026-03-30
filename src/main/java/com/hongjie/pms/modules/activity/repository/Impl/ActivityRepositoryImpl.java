package com.hongjie.pms.modules.activity.repository.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.repository.ActivityRepository;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public class ActivityRepositoryImpl implements ActivityRepository {
    @Override
    public boolean existsById(Long id) {
        return false;
    }
}
