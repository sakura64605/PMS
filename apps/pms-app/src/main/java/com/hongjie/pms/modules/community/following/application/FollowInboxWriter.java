package com.hongjie.pms.modules.community.following.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.platform.enums.PostType;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.feed.infrastructure.persistence.entity.UserInbox;
import com.hongjie.pms.modules.community.feed.infrastructure.persistence.mapper.UserInboxMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

/** Bounded history projection, participating in the caller's relationship transaction. */
@Component
@RequiredArgsConstructor
public class FollowInboxWriter {
    private static final int HISTORY_LIMIT = 20;
    private final UserInboxMapper inboxes;
    private final PetPostMapper pets;
    private final ActivityMapper activities;

    public void remove(Long follower, Long target) {
        inboxes.delete(
                new LambdaQueryWrapper<UserInbox>()
                        .eq(UserInbox::getUserId, follower)
                        .eq(UserInbox::getPosterId, target));
    }

    public void populate(Long follower, User author) {
        populate(follower, author, HISTORY_LIMIT);
    }

    public void populate(Long follower, User author, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        // Rebuild this pair only, including stale rows left from an earlier unfollow.
        remove(follower, author.getId());
        List<UserInbox> rows = new ArrayList<>();
        for (var pet :
                pets.selectList(
                        new LambdaQueryWrapper<PetPost>()
                                .eq(PetPost::getUserId, author.getId())
                                .eq(PetPost::getAuditStatus, 1)
                                .eq(PetPost::getStatus, 1)
                                .orderByDesc(PetPost::getCreateTime)
                                .orderByDesc(PetPost::getId)
                                .last("LIMIT " + limit))) {
            rows.add(
                    row(
                            follower,
                            author,
                            pet.getId(),
                            PostType.PET,
                            pet.getTitle(),
                            pet.getImages(),
                            pet.getCreateTime()));
        }
        for (var activity :
                activities.selectList(
                        new LambdaQueryWrapper<Activity>()
                                .eq(Activity::getUserId, author.getId())
                                .eq(Activity::getAuditStatus, 1)
                                .eq(Activity::getStatus, 1)
                                .eq(Activity::getDeleted, 0)
                                .orderByDesc(Activity::getCreateTime)
                                .orderByDesc(Activity::getId)
                                .last("LIMIT " + limit))) {
            rows.add(
                    row(
                            follower,
                            author,
                            activity.getId(),
                            PostType.ACTIVITY,
                            activity.getTitle(),
                            activity.getImages(),
                            activity.getCreateTime()));
        }
        rows.sort(
                Comparator.comparing(
                                UserInbox::getCreateTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(UserInbox::getPostId, Comparator.reverseOrder())
                        .thenComparing(UserInbox::getPostType));
        rows.stream().limit(limit).forEach(inboxes::insert);
    }

    private UserInbox row(
            Long follower,
            User author,
            Long id,
            PostType type,
            String title,
            List<String> images,
            LocalDateTime time) {
        var row = new UserInbox();
        row.setUserId(follower);
        row.setPosterId(author.getId());
        row.setPosterName(author.getUserName());
        row.setPosterAvatar(author.getAvatar());
        row.setPostId(id);
        row.setPostType(type.getCode());
        row.setTitle(title);
        row.setCoverImage(images == null || images.isEmpty() ? null : images.get(0));
        row.setCreateTime(time);
        row.setIsRead(0);
        return row;
    }
}
