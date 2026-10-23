package com.hongjie.pms.refactor.feed;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.feed.dto.FeedDto;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.core.page.PageQuery;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.following.FollowInboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Primary
@RequiredArgsConstructor
public class PmsFeedServiceImpl implements FeedService {
    private static final int PUSH_THRESHOLD=1000;
    private final FeedReadMapper reads;
    private final FeedWriteMapper writes;
    private final FeedAssembler assembler;
    private final FollowInboxWriter history;
    private final UserMapper users;
    private final TargetTypeCodec codec;

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.REPEATABLE_READ)
    public IPage<FeedDto> getHomeFeed(Integer number,Integer size){
        Long actor=currentUser();var query=PageQuery.of(number==null?null:number.longValue(),size==null||size<=0?20L:size.longValue());
        long page=query.normalizedPageNum(),limit=query.normalizedPageSize(),total=reads.total(actor);
        var result=new Page<FeedDto>(page,limit,total);
        if((page-1)*limit>=total){result.setRecords(List.of());return result;}
        var keys=reads.page(actor,(page-1)*limit,limit);var records=assembler.assemble(keys);result.setRecords(records);
        Set<String> returned=new HashSet<>();records.forEach(row->returned.add(row.getPostType()+":"+row.getPostId()));
        var delivered=keys.stream().filter(key->returned.contains(key.getPostType()+":"+key.getPostId())).toList();
        if(!delivered.isEmpty())reads.markRead(actor,delivered);
        return result;
    }
    @Override public int getUnreadCount(){return (int)Math.min(Integer.MAX_VALUE,reads.unread(currentUser()));}

    @Override @Transactional(rollbackFor=Exception.class)
    public void pushToFans(Long author,Long post,String type,String title,List<String> images,String name,String avatar,LocalDateTime time){
        validId(author);validId(post);var target=codec.read(type);
        String normalized;if(target==BizTargetType.PET_POST)normalized="pet";else if(target==BizTargetType.PET_ACTIVITY)normalized="activity";else throw new BusinessException(ErrorCode.PARAM_ERROR,"不支持的动态类型");
        if(writes.lockAuthor(author)==null)throw new BusinessException(ErrorCode.NOT_FOUND,"发布者不存在");
        int fans=writes.fans(author);writes.mode(author,fans,fans>PUSH_THRESHOLD?1:0);
        if(fans==0||fans>PUSH_THRESHOLD)return;
        writes.fanOut(author,post,normalized,name,avatar,title,images==null||images.isEmpty()?null:images.get(0),time==null?LocalDateTime.now():time);
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public void onUnfollow(Long follower,Long target){lockPair(follower,target);history.remove(follower,target);}
    @Override @Transactional(rollbackFor=Exception.class)
    public void pullHistoryPosts(Long follower,Long target,int limit){
        lockPair(follower,target);
        var author=users.selectById(target);if(author==null)throw new BusinessException(ErrorCode.NOT_FOUND,"发布者不存在");
        history.populate(follower,author,Math.max(1,Math.min(100,limit)));
    }
    private void lockPair(Long follower,Long target){
        validId(follower);validId(target);
        if(writes.lockAuthor(Math.min(follower,target))==null)throw new BusinessException(ErrorCode.NOT_FOUND,"用户不存在");
        if(!follower.equals(target)&&writes.lockAuthor(Math.max(follower,target))==null)throw new BusinessException(ErrorCode.NOT_FOUND,"用户不存在");
    }
    private Long currentUser(){Long id=UserContext.getUserId();if(id==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);return id;}
    private void validId(Long id){if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);}
}
