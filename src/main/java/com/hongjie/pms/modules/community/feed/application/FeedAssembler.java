package com.hongjie.pms.modules.community.feed.application;

import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.feed.api.dto.FeedDto;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FeedAssembler {
    private final PetPostMapper pets;
    private final ActivityMapper activities;
    private final UserMapper users;

    public List<FeedDto> assemble(List<FeedKey> keys){
        if(keys.isEmpty())return List.of();
        List<Long> petIds=keys.stream().filter(k->k.getPostType().equals("pet")).map(FeedKey::getPostId).toList();
        List<Long> activityIds=keys.stream().filter(k->k.getPostType().equals("activity")).map(FeedKey::getPostId).toList();
        Map<Long,PetPost> petMap=petIds.isEmpty()?Map.of():index(pets.selectBatchIds(petIds),PetPost::getId);
        Map<Long,Activity> activityMap=activityIds.isEmpty()?Map.of():index(activities.selectBatchIds(activityIds),Activity::getId);
        Map<Long,User> people=index(users.selectBatchIds(keys.stream().map(FeedKey::getPosterId).collect(Collectors.toSet())),User::getId);
        List<FeedDto> result=new ArrayList<>();
        for(var key:keys){
            var builder=FeedDto.builder().id(key.getPostId()).postId(key.getPostId()).postType(key.getPostType()).posterId(key.getPosterId()).createTime(key.getCreateTime());
            if(key.getPostType().equals("pet")){
                var row=petMap.get(key.getPostId());if(row==null)continue;
                builder.title(row.getTitle()).content(row.getContent()).coverImage(cover(row.getImages())).viewCount(row.getViewCount()).likeCount(row.getLikeCount()).commentCount(row.getCommentCount());
            }else{
                var row=activityMap.get(key.getPostId());if(row==null)continue;
                builder.title(row.getTitle()).content(row.getContent()).coverImage(cover(row.getImages())).viewCount(row.getViewCount()).likeCount(row.getLikeCount()).commentCount(row.getCommentCount());
            }
            var author=people.get(key.getPosterId());if(author!=null)builder.posterName(author.getUserName()).posterAvatar(author.getAvatar());
            result.add(builder.build());
        }
        return result;
    }
    private static String cover(List<String> images){return images==null||images.isEmpty()?null:images.get(0);}
    private static <T> Map<Long,T> index(List<T> rows,Function<T,Long> id){return rows.stream().collect(Collectors.toMap(id,Function.identity()));}
}
