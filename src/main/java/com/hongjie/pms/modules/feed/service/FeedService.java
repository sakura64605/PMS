package com.hongjie.pms.modules.feed.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.feed.dto.FeedDto;

import java.time.LocalDateTime;
import java.util.List;

public interface FeedService {

    /**
     * 获取首页 Feed 流
     */
    IPage<FeedDto> getHomeFeed(Integer pageNum, Integer pageSize);

    /**
     * 获取未读消息数量
     */
    int getUnreadCount();

    /**
     * 发布帖子时推送给粉丝
     */
    void pushToFans(Long userId, Long postId, String postType, 
                    String title, List<String> images, 
                    String posterName, String posterAvatar, 
                    LocalDateTime createTime);

    /**
     * 取消关注时清理收件箱
     */
    void onUnfollow(Long followerId, Long followingId);

    void pullHistoryPosts(Long currentUserId, Long userId, int i);
}