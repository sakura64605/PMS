package com.hongjie.pms.refactor.daily;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.daily.dto.*;
import com.hongjie.pms.modules.daily.entity.*;
import com.hongjie.pms.modules.daily.mapper.*;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.refactor.audit.AuditCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class DailyCommands {
    private final DailyPostMapper posts;
    private final TopicMapper topics;
    private final DailyTopicRelMapper links;
    private final UserMapper users;
    private final DailyDataMapper writes;
    private final AuditCommandService audits;
    private final AuditRecordMapper records;
    private final DailyAssembler assembler;
    private final ApplicationEventPublisher events;
    @Transactional public DailyPostDto publish(PublishDailyRequest request) {
        Long actor=actor();
        if(request==null||request.getDailyPost()==null)throw new BusinessException(ErrorCode.PARAM_ERROR);
        var input=request.getDailyPost();
        if(!StringUtils.hasText(input.getContent())&&(input.getImages()==null||input.getImages().isEmpty())&&!StringUtils.hasText(input.getVideoUrl()))
            throw new BusinessException(ErrorCode.PARAM_ERROR,"文字、图片或视频至少填写一项");
        if(input.getContent()!=null&&input.getContent().length()>10000 || input.getVideoUrl()!=null&&input.getVideoUrl().length()>500
                || input.getLocation()!=null&&input.getLocation().length()>200
                || input.getImages()!=null&&(input.getImages().size()>20||input.getImages().stream().anyMatch(image->!StringUtils.hasText(image))))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        var user=users.selectById(actor);
        if(user==null||!Objects.equals(user.getStatus(),1))throw new BusinessException(ErrorCode.USER_DISABLED);
        if(Objects.equals(user.getIsMuted(),1)&&(user.getMuteEndTime()==null||user.getMuteEndTime().isAfter(LocalDateTime.now())))
            throw new BusinessException(ErrorCode.FORBIDDEN,"当前账号处于禁言期");
        Set<Long> topicIds=new TreeSet<>();
        if(request.getTopicIds()!=null){
            if(request.getTopicIds().size()>20||request.getTopicIds().stream().anyMatch(id->id==null||id<=0))throw new BusinessException(ErrorCode.PARAM_ERROR);
            topicIds.addAll(request.getTopicIds());
        } else if(input.getTopicId()!=null)topicIds.add(input.getTopicId());
        DailyPost post=new DailyPost();post.setUserId(actor);post.setContent(Objects.requireNonNullElse(input.getContent(),""));
        post.setImages(input.getImages()==null?List.of():List.copyOf(input.getImages()));post.setVideoUrl(input.getVideoUrl());post.setLocation(input.getLocation());
        post.setTopicId(topicIds.isEmpty()?null:topicIds.iterator().next());post.setAuditStatus(0);post.setStatus(1);
        post.setLikeCount(0);post.setViewCount(0);post.setCommentCount(0);post.setShareCount(0);post.setCreateTime(LocalDateTime.now());
        if(posts.insert(post)!=1)throw new BusinessException(ErrorCode.DB_ERROR);
        if(!topicIds.isEmpty()){
            var valid=topics.selectList(new LambdaQueryWrapper<Topic>().in(Topic::getId,topicIds).eq(Topic::getStatus,1)
                    .orderByAsc(Topic::getId).last("FOR UPDATE"));
            if(valid.size()!=topicIds.size())throw new BusinessException(ErrorCode.PARAM_ERROR,"话题不存在或已停用");
            for(Long topicId:topicIds){
                DailyTopicRel link=new DailyTopicRel();link.setDailyId(post.getId());link.setTopicId(topicId);links.insert(link);
                if(writes.topicCount(topicId,1)!=1)throw new BusinessException(ErrorCode.DB_ERROR);
            }
        }
        audits.submit("daily",post.getId());events.publishEvent(new DailyChanged(post.getId()));
        return assembler.batch(List.of(post),actor).get(0);
    }
    @Transactional public void delete(Long id) {
        Long actor=actor();
        if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);
        DailyPost post=posts.selectOne(new LambdaQueryWrapper<DailyPost>().eq(DailyPost::getId,id).last("FOR UPDATE"));
        if(post==null||!Objects.equals(post.getStatus(),1))throw new BusinessException(ErrorCode.NOT_FOUND);
        if(!actor.equals(post.getUserId())&&!UserContext.isAdmin())throw new BusinessException(ErrorCode.FORBIDDEN);
        if(posts.update(null,new LambdaUpdateWrapper<DailyPost>().eq(DailyPost::getId,id).eq(DailyPost::getStatus,1).set(DailyPost::getStatus,0))!=1)
            throw new BusinessException(ErrorCode.DB_ERROR);
        var topicIds=links.getTopicIdsByDailyId(id).stream().distinct().sorted().toList();
        if(!topicIds.isEmpty()){
            topics.selectList(new LambdaQueryWrapper<Topic>().in(Topic::getId,topicIds).orderByAsc(Topic::getId).last("FOR UPDATE"));
            links.delete(new LambdaQueryWrapper<DailyTopicRel>().eq(DailyTopicRel::getDailyId,id));
            for(Long topicId:topicIds)writes.topicCount(topicId,-1);
        }
        // Withdraw only open reviews; completed decisions remain as history.
        records.delete(new LambdaQueryWrapper<AuditRecord>().eq(AuditRecord::getTargetType,"daily")
                .eq(AuditRecord::getTargetId,id).eq(AuditRecord::getAuditStatus,0));
        events.publishEvent(new DailyChanged(id));
    }
    private Long actor(){Long id=UserContext.getUserId();if(id==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);return id;}
}
