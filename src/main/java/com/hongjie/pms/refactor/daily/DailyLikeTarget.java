package com.hongjie.pms.refactor.daily;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.refactor.like.LikeTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component @RequiredArgsConstructor
public class DailyLikeTarget implements LikeTarget {
    private final DailyPostMapper posts;
    private final DailyDataMapper writes;
    private final ApplicationEventPublisher events;
    @Override public String type(){return "daily";}
    @Override public Snapshot load(Long id){
        DailyPost row=posts.selectOne(new LambdaQueryWrapper<DailyPost>().eq(DailyPost::getId,id).last("FOR UPDATE"));
        if(row==null||!Objects.equals(row.getStatus(),1)||!Objects.equals(row.getAuditStatus(),1))
            throw new BusinessException(ErrorCode.NOT_FOUND);
        return new Snapshot(row.getUserId(),row.getContent(),"/daily/"+id,Objects.requireNonNullElse(row.getLikeCount(),0));
    }
    @Override public void increment(Long id){if(writes.like(id)!=1)throw new BusinessException(ErrorCode.DB_ERROR);events.publishEvent(new DailyChanged(id));}
    @Override public void decrement(Long id){if(writes.unlike(id)!=1)throw new BusinessException(ErrorCode.DB_ERROR);events.publishEvent(new DailyChanged(id));}
}
