package com.hongjie.pms.refactor.search;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.search.dto.SearchResultItem;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.refactor.daily.DailyAssembler;
import org.springframework.context.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;

@Configuration(proxyBeanMethods=false)
public class SearchSources {
    @Bean SearchSource petSearchSource(PetPostMapper mapper) {
        return source("pet",mapper,p->p.getId(),(p,actor)->live(p.getStatus(),Set.of(1,2),p.getAuditStatus(),p.getUserId(),actor),
                p->base(p.getId(),p.getUserId(),p.getTitle(),p.getContent(),p.getImages(),p.getLikeCount(),p.getCommentCount(),p.getViewCount(),p.getCreateTime())
                        .petName(p.getPetName()).petType(p.getPetType()).petAge(p.getPetAge()).petGender(p.getPetGender())
                        .address(p.getAddress()).location(p.getAddress()).build());
    }
    @Bean SearchSource activitySearchSource(ActivityMapper mapper) {
        return source("activity",mapper,a->a.getId(),(a,actor)->Objects.equals(a.getDeleted(),0)
                        && live(a.getStatus(),Set.of(0,1,2),a.getAuditStatus(),a.getUserId(),actor),
                a->base(a.getId(),a.getUserId(),a.getTitle(),a.getContent(),a.getImages(),a.getLikeCount(),a.getCommentCount(),a.getViewCount(),a.getCreateTime())
                        .location(a.getLocation()).maxPeople(a.getMaxPeople()).currentPeople(a.getCurrentPeople())
                        .startTime(a.getStartTime()).endTime(a.getEndTime()).build());
    }
    @Bean SearchSource dailySearchSource(DailyPostMapper mapper,DailyAssembler assembler) {
        return new SearchSource() {
            public String type(){return "daily";}
            public Map<Long,SearchResultItem> load(Set<Long> ids,Long actor){
                var posts=mapper.selectBatchIds(ids).stream()
                        .filter(p->Objects.equals(p.getStatus(),1) && (Objects.equals(p.getAuditStatus(),1)||Objects.equals(actor,p.getUserId())&&actor!=null)).toList();
                var topics=assembler.topicMap(posts.stream().map(p->p.getId()).toList());
                Map<Long,SearchResultItem> result=new HashMap<>();
                for(var p:posts){
                    String content=p.getContent();String title=content==null?"":content.lines().findFirst().orElse("");
                    if(title.length()>50)title=title.substring(0,50);
                    var item=base(p.getId(),p.getUserId(),title,content,p.getImages(),p.getLikeCount(),p.getCommentCount(),p.getViewCount(),p.getCreateTime())
                            .location(p.getLocation()).topics(topics.getOrDefault(p.getId(),List.of()).stream().map(t->t.getName()).toList()).build();
                    item.setType(type());result.put(p.getId(),item);
                }
                return result;
            }
        };
    }
    private boolean live(Integer status,Set<Integer> publicStates,Integer audit,Long owner,Long actor){
        if(status==null||status==-1)return false;
        return actor!=null&&actor.equals(owner) || Objects.equals(audit,1)&&publicStates.contains(status);
    }
    private SearchResultItem.SearchResultItemBuilder base(Long id,Long owner,String title,String content,List<String> images,
            Integer likes,Integer comments,Integer views,LocalDateTime created) {
        return SearchResultItem.builder().id(id).title(title).content(content)
                .images(images==null||images.isEmpty()?null:images.stream().filter(Objects::nonNull).limit(1).toList())
                .user(UserSimpleDto.builder().userId(owner).build()).likeCount(Objects.requireNonNullElse(likes,0))
                .commentCount(Objects.requireNonNullElse(comments,0)).viewCount(Objects.requireNonNullElse(views,0))
                .createTime(created).isLiked(false).isFavorite(false).isSignedUp(0);
    }
    private <T> SearchSource source(String type,BaseMapper<T> mapper,Function<T,Long> id,
            BiPredicate<T,Long> visible,Function<T,SearchResultItem> convert) {
        return new SearchSource(){
            public String type(){return type;}
            public Map<Long,SearchResultItem> load(Set<Long> ids,Long actor){
                Map<Long,SearchResultItem> result=new HashMap<>();
                for(T row:mapper.selectBatchIds(ids))if(visible.test(row,actor)){
                    var item=convert.apply(row);item.setType(type);result.put(id.apply(row),item);
                }
                return result;
            }
        };
    }
}
