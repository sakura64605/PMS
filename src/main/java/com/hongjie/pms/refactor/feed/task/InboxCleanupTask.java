package com.hongjie.pms.refactor.feed.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.feed.entity.UserInbox;
import com.hongjie.pms.modules.feed.mapper.UserInboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxCleanupTask {

    private final UserInboxMapper userInboxMapper;

    /**
     * 每天凌晨2点执行，清理30天前的已读消息
     */
    public void cleanupOldReadMessages() {
        log.info("开始清理收件箱已读消息...");
        long startTime = System.currentTimeMillis();

        LocalDateTime expireTime = LocalDateTime.now().minusDays(30);

        LambdaQueryWrapper<UserInbox> wrapper = new LambdaQueryWrapper<UserInbox>()
                .eq(UserInbox::getIsRead, 1)
                .lt(UserInbox::getCreateTime, expireTime);

        int deletedCount = userInboxMapper.delete(wrapper);

        long endTime = System.currentTimeMillis();
        log.info("收件箱清理完成，删除 {} 条消息，耗时 {}ms", deletedCount, endTime - startTime);
    }
}
