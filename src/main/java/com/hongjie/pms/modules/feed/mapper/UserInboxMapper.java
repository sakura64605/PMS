package com.hongjie.pms.modules.feed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.feed.entity.UserInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserInboxMapper extends BaseMapper<UserInbox> {
    /**
     * 批量标记消息为已读（使用 IN 查询）
     * @param ids 消息ID列表
     * @param readTime 阅读时间
     * @return 更新成功的条数
     */
    int batchMarkAsReadList(@Param("ids") List<Long> ids, @Param("readTime") LocalDateTime readTime);

    /**
     * 批量标记消息为已读（使用 CASE WHEN，性能更好）
     * @param list 消息列表（包含 id 和 readTime）
     * @return 更新成功的条数
     */
    int batchMarkAsReadWithCase(@Param("list") List<UserInbox> list);

    /**
     * 批量删除已读消息（清理任务使用）
     * @param userId 用户ID
     * @param expireTime 过期时间
     * @return 删除条数
     */
    int batchDeleteOldReadMessages(@Param("userId") Long userId, @Param("expireTime") LocalDateTime expireTime);
}