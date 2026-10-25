package com.hongjie.pms.refactor.search.index;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hongjie.pms.modules.search.document.UnifiedDoc;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.refactor.daily.DailyAssembler;
import org.springframework.context.annotation.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods=false)
public class SearchIndexSources {
    @Bean public SearchIndexSource petIndexSource(PetPostMapper posts,UserMapper users){
        return source("pet","pet_",posts,users,p->p.getId(),p->p.getUserId(),p->p.getStatus()!=null&&p.getStatus()!=-1,
                (p,u)->UnifiedDoc.builder().title(p.getTitle()).content(p.getContent()).images(p.getImages())
                    .likeCount(p.getLikeCount()).commentCount(p.getCommentCount()).viewCount(p.getViewCount())
                    .status(p.getStatus()).auditStatus(p.getAuditStatus()).createTime(p.getCreateTime()).updateTime(p.getUpdateTime())
                    .petName(p.getPetName()).petType(p.getPetType()).petAge(p.getPetAge()).petGender(p.getPetGender()).address(p.getAddress()),
                ids->Map.of());
    }
    @Bean public SearchIndexSource activityIndexSource(ActivityMapper posts,UserMapper users){
        return source("activity","activity_",posts,users,p->p.getId(),p->p.getUserId(),p->Objects.equals(p.getDeleted(),0),
                (p,u)->UnifiedDoc.builder().title(p.getTitle()).content(p.getContent()).images(p.getImages()).location(p.getLocation())
                    .likeCount(p.getLikeCount()).commentCount(p.getCommentCount()).viewCount(p.getViewCount())
                    .status(p.getStatus()).auditStatus(p.getAuditStatus()).createTime(p.getCreateTime()).updateTime(p.getUpdateTime())
                    .maxPeople(p.getMaxPeople()).currentPeople(p.getCurrentPeople()).startTime(p.getStartTime()).endTime(p.getEndTime()),
                ids->Map.of());
    }
    @Bean public SearchIndexSource dailyIndexSource(DailyPostMapper posts,UserMapper users,DailyAssembler assembler){
        return source("daily","daily_",posts,users,p->p.getId(),p->p.getUserId(),p->Objects.equals(p.getStatus(),1),
                (p,u)->UnifiedDoc.builder().title(title(p.getContent())).content(p.getContent()).images(p.getImages()).location(p.getLocation())
                    .likeCount(p.getLikeCount()).commentCount(p.getCommentCount()).viewCount(p.getViewCount())
                    .status(p.getStatus()).auditStatus(p.getAuditStatus()).createTime(p.getCreateTime()).updateTime(p.getCreateTime()).videoUrl(p.getVideoUrl()),
                ids->assembler.topicMap(ids).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        e->e.getValue().stream().map(t->t.getName()).toList())));
    }
    private String title(String content){
        String line=content==null?"":content.lines().findFirst().orElse("");return line.length()>50?line.substring(0,50):line;
    }
    private <T> SearchIndexSource source(String type,String prefix,BaseMapper<T> mapper,UserMapper users,
            Function<T,Long> id,Function<T,Long> owner,Predicate<T> alive,
            BiFunction<T,User,UnifiedDoc.UnifiedDocBuilder> convert,Function<List<Long>,Map<Long,List<String>>> topics){
        return new SearchIndexSource(){
            public String type(){return type;}public String prefix(){return prefix;}
            public long maxId(){
                var last=mapper.selectList(new QueryWrapper<T>().select("id").orderByDesc("id").last("LIMIT 1"));
                return last.isEmpty()?0:id.apply(last.get(0));
            }
            public List<Long> ids(long after,long through){
                return mapper.selectList(new QueryWrapper<T>().select("id").gt("id",after).le("id",through)
                        .orderByAsc("id").last("LIMIT 100")).stream().map(id).toList();
            }
            public Map<Long,UnifiedDoc> load(List<Long> ids){
                if(ids.isEmpty())return Map.of();
                var rows=mapper.selectBatchIds(ids).stream().filter(alive).toList();
                var owners=rows.stream().map(owner).filter(Objects::nonNull).distinct().toList();
                Map<Long,User> authors=owners.isEmpty()?Map.of():users.selectBatchIds(owners).stream().collect(Collectors.toMap(User::getId,u->u));
                var names=topics.apply(rows.stream().map(id).toList());Map<Long,UnifiedDoc> result=new HashMap<>();
                for(T row:rows){
                    User author=authors.get(owner.apply(row));Long key=id.apply(row);
                    var doc=convert.apply(row,author).id(prefix+key).type(type).businessId(key).userId(owner.apply(row))
                            .userName(author==null?"已注销":author.getUserName()).userAvatar(author==null?null:author.getAvatar())
                            .topics(names.getOrDefault(key,List.of())).build();
                    result.put(key,doc);
                }
                return result;
            }
        };
    }
}
