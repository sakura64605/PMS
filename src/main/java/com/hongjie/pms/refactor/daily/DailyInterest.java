package com.hongjie.pms.refactor.daily;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.daily.entity.*;
import com.hongjie.pms.modules.daily.mapper.*;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.daily.service.DailyCacheInvalidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class DailyInterest {
    private final DailyPostMapper posts;
    private final UserMapper users;
    private final DailyUserBehaviorMapper behaviors;
    private final DailyUserInterestMapper interests;
    private final DailyTopicRelMapper relations;
    private final TopicMapper topics;
    private final DailyCacheInvalidator cache;
    private static final Map<String,Double> WEIGHTS=Map.of("view",0.3,"like",1.0,"share",1.5,"follow",0.1);
    public Map<String,Double> read(Long actor){
        if(actor==null)return Map.of();
        DailyUserInterest row=interests.selectById(actor);
        if(row==null||row.getInterestJson()==null)return Map.of();
        Map<String,Double> result=new HashMap<>();
        try{JSON.parseObject(row.getInterestJson()).forEach((key,value)->{
            if(value instanceof Number number&&Double.isFinite(number.doubleValue())&&number.doubleValue()>=0)result.put(key,number.doubleValue());
        });}catch(RuntimeException malformed){return Map.of();}
        return result;
    }
    public List<Long> top(Long actor,int limit){
        return read(actor).entrySet().stream().filter(entry->entry.getKey().matches("topic_[0-9]{1,18}"))
                .sorted(Map.Entry.<String,Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(0,Math.min(limit,20))).map(entry->Long.parseLong(entry.getKey().substring(6))).toList();
    }
    @Transactional public void record(Long actor,Long id,String action){
        if(actor==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if(!Objects.equals(actor,UserContext.getUserId()))throw new BusinessException(ErrorCode.FORBIDDEN);
        if(id==null||id<=0||!WEIGHTS.containsKey(Objects.requireNonNullElse(action,"")))throw new BusinessException(ErrorCode.PARAM_ERROR);
        var post=posts.selectOne(new LambdaQueryWrapper<DailyPost>().eq(DailyPost::getId,id).last("FOR UPDATE"));
        if(post==null||!Objects.equals(post.getStatus(),1)||!Objects.equals(post.getAuditStatus(),1))throw new BusinessException(ErrorCode.NOT_FOUND);
        var user=users.selectOne(new LambdaQueryWrapper<User>().eq(User::getId,actor).last("FOR UPDATE"));
        if(user==null||!Objects.equals(user.getStatus(),1))throw new BusinessException(ErrorCode.USER_DISABLED);
        Map<String,Double> profile=new HashMap<>(read(actor));
        var ids=relations.getTopicIdsByDailyId(id);
        if(!ids.isEmpty())for(var topic:topics.selectBatchIds(ids)){
            if(!Objects.equals(topic.getStatus(),1))continue;
            profile.merge("topic_"+topic.getId(),WEIGHTS.get(action),Double::sum);
            profile.merge("tag_"+topic.getName(),WEIGHTS.get(action),Double::sum);
        }
        DailyUserBehavior event=new DailyUserBehavior();event.setUserId(actor);event.setTargetId(id);event.setActionType(action);event.setActionTime(LocalDateTime.now());
        behaviors.insert(event);
        DailyUserInterest row=new DailyUserInterest();row.setUserId(actor);row.setInterestJson(JSON.toJSONString(profile));row.setUpdateTime(LocalDateTime.now());
        if(interests.selectById(actor)==null)interests.insert(row);else interests.updateById(row);
        cache.viewerAfterCommit(actor);
    }
}
