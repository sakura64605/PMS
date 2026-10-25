package com.hongjie.pms.modules.governance.report.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.modules.scheduling.application.DelayTaskService;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.entity.Comment;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.mapper.CommentMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.enums.biz.ActivityStatus;
import org.springframework.context.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;

@Configuration(proxyBeanMethods=false)
public class ReportTargets {
    @Bean ReportTarget petReportTarget(PetPostMapper mapper,ReportWriteMapper locks){
        return target(BizTargetType.PET_POST,"宠物",id->locks.lockPet(id)!=null,
                ids->titles(mapper.selectBatchIds(ids),PetPost::getId,PetPost::getTitle),
                id->mapper.update(null,new LambdaUpdateWrapper<PetPost>().eq(PetPost::getId,id).eq(PetPost::getStatus,1).set(PetPost::getStatus,3)));
    }
    @Bean ReportTarget activityReportTarget(ActivityMapper mapper,ReportWriteMapper locks){
        return target(BizTargetType.PET_ACTIVITY,"活动",id->locks.lockActivity(id)!=null,
                ids->titles(mapper.selectBatchIds(ids),Activity::getId,Activity::getTitle),
                id->mapper.update(null,new LambdaUpdateWrapper<Activity>().eq(Activity::getId,id).eq(Activity::getDeleted,0)
                        .in(Activity::getStatus,0,1).set(Activity::getStatus,ActivityStatus.OFFLINE.getCode())));
    }
    @Bean ReportTarget commentReportTarget(CommentMapper mapper,UserMapper users,ReportWriteMapper locks,DelayTaskService tasks){
        return target(BizTargetType.PET_COMMENT,"评论",id->locks.lockComment(id)!=null,
                ids->titles(mapper.selectBatchIds(ids),Comment::getId,Comment::getContent),id->{
                    var comment=mapper.selectById(id);
                    if(comment==null)return;
                    int changed=mapper.update(null,new LambdaUpdateWrapper<Comment>().eq(Comment::getId,id).eq(Comment::getStatus,1).set(Comment::getStatus,0));
                    if(changed!=1||comment.getUserId()==null)return;
                    User user=locks.lockUser(comment.getUserId());
                    if(user==null)return;
                    LocalDateTime until=LocalDateTime.now().plusDays(1);
                    if(user.getMuteEndTime()!=null&&user.getMuteEndTime().isAfter(until))until=user.getMuteEndTime();
                    users.update(null,new LambdaUpdateWrapper<User>().eq(User::getId,user.getId()).set(User::getIsMuted,1).set(User::getMuteEndTime,until));
                    tasks.addTask("MUTED_PUNISHMENT_END",user.getId(),until);
                });
    }
    @Bean ReportTarget userReportTarget(UserMapper mapper,ReportWriteMapper locks,DelayTaskService tasks){
        return target(BizTargetType.USER,"用户",id->locks.lockUser(id)!=null,
                ids->titles(mapper.selectBatchIds(ids),User::getId,User::getUserName),id->{
                    int changed=mapper.update(null,new LambdaUpdateWrapper<User>().eq(User::getId,id).eq(User::getStatus,1).set(User::getStatus,0));
                    if(changed==1)tasks.addTask("BANNED_PUNISHMENT_END",id,LocalDateTime.now().plusDays(1));
                });
    }
    private static <T> Map<Long,String> titles(List<T> rows,Function<T,Long> id,Function<T,String> title){
        Map<Long,String> result=new HashMap<>();for(T row:rows)result.put(id.apply(row),title.apply(row));return result;
    }
    private static ReportTarget target(BizTargetType type,String description,Predicate<Long> lock,
            Function<Collection<Long>,Map<Long,String>> lookup,Consumer<Long> accept){
        return new ReportTarget(){
            public BizTargetType type(){return type;}
            public String description(){return description;}
            public boolean lock(Long id){return lock.test(id);}
            public Map<Long,String> titles(Collection<Long> ids){return ids.isEmpty()?Map.of():lookup.apply(ids);}
            public void accept(Long id){accept.accept(id);}
        };
    }
}
