package com.hongjie.pms.refactor.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.petpost.entity.FavoriteRecord;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.search.dto.SearchResultItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component @RequiredArgsConstructor
public class SearchEnrichment {
    private final UserMapper users;
    private final LikeRecordMapper likes;
    private final FavoriteRecordMapper favorites;
    private final ActivitySignupMapper signups;
    public void enrich(List<SearchResultItem> items,Long actor){
        if(items.isEmpty())return;
        var authorIds=items.stream().map(i->i.getUser().getUserId()).filter(Objects::nonNull).distinct().toList();
        Map<Long,User> authors=authorIds.isEmpty()?Map.of():users.selectBatchIds(authorIds).stream().collect(Collectors.toMap(User::getId,u->u));
        for(var item:items){
            Long id=item.getUser().getUserId();var user=authors.get(id);
            item.setUser(UserSimpleDto.builder().userId(id).username(user==null?"已注销":user.getUserName())
                    .nickname(user==null?"已注销":user.getNickName()).avatar(user==null?null:user.getAvatar()).build());
        }
        if(actor==null)return;
        var ids=items.stream().map(SearchResultItem::getId).distinct().toList();
        Set<String> liked=likes.selectList(new LambdaQueryWrapper<LikeRecord>().eq(LikeRecord::getUserId,actor)
                .in(LikeRecord::getTargetId,ids).in(LikeRecord::getTargetType,List.of("pet_post","pet_activity","daily")))
                .stream().map(r->r.getTargetType()+":"+r.getTargetId()).collect(Collectors.toSet());
        for(var item:items){
            String type="pet".equals(item.getType())?"pet_post":"activity".equals(item.getType())?"pet_activity":"daily";
            item.setIsLiked(liked.contains(type+":"+item.getId()));
        }
        var petIds=items.stream().filter(i->"pet".equals(i.getType())).map(SearchResultItem::getId).toList();
        if(!petIds.isEmpty()){
            Set<Long> saved=favorites.selectList(new LambdaQueryWrapper<FavoriteRecord>().eq(FavoriteRecord::getUserId,actor)
                    .in(FavoriteRecord::getTargetId,petIds).eq(FavoriteRecord::getTargetType,"pet_post"))
                    .stream().map(FavoriteRecord::getTargetId).collect(Collectors.toSet());
            items.stream().filter(i->"pet".equals(i.getType())).forEach(i->i.setIsFavorite(saved.contains(i.getId())));
        }
        var activityIds=items.stream().filter(i->"activity".equals(i.getType())).map(SearchResultItem::getId).toList();
        if(!activityIds.isEmpty()){
            Map<Long,Integer> states=signups.selectList(new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getUserId,actor)
                    .in(ActivitySignup::getActivityId,activityIds)).stream()
                    .collect(Collectors.toMap(ActivitySignup::getActivityId,s->Objects.requireNonNullElse(s.getStatus(),0)));
            items.stream().filter(i->"activity".equals(i.getType())).forEach(i->i.setIsSignedUp(Set.of(1,3,4).contains(states.getOrDefault(i.getId(),0))?1:0));
        }
    }
}
