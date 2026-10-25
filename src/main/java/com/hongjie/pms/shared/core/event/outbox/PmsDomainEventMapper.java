package com.hongjie.pms.shared.core.event.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发件箱 Mapper。
 *
 * <p>无自定义 SQL —— 增删改查与状态流转全部由 MyBatis-Plus 的 {@code BaseMapper}
 * 加 {@code LambdaUpdateWrapper} 表达，不需要写 XML。
 */
@Mapper
public interface PmsDomainEventMapper extends BaseMapper<PmsDomainEvent> {
}
