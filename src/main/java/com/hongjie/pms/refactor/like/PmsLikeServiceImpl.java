package com.hongjie.pms.refactor.like;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.like.dto.request.LikeRequest;
import com.hongjie.pms.modules.like.dto.response.LikeResponseDto;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.like.service.LikeService;
import com.hongjie.pms.refactor.core.strategy.StrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Primary
@RequiredArgsConstructor
public class PmsLikeServiceImpl implements LikeService {
    private final StrategyRegistry<String, LikeTarget> likeTargetRegistry;
    private final LikeRecordMapper records;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeResponseDto like(LikeRequest request) {
        Long actor = UserContext.getUserId();
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (request == null || request.getTargetId() == null || request.getTargetId() <= 0)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        LikeTarget strategy = likeTargetRegistry.get(request.getTargetType());
        var snapshot = strategy.load(request.getTargetId());
        var existing = records.selectOne(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, actor).eq(LikeRecord::getTargetId, request.getTargetId())
                .eq(LikeRecord::getTargetType, strategy.type()));
        boolean liked = existing == null;
        if (liked) {
            records.insert(LikeRecord.builder().userId(actor).targetId(request.getTargetId())
                    .targetType(strategy.type()).createTime(LocalDateTime.now()).build());
            strategy.increment(request.getTargetId());
        } else {
            if (records.deleteById(existing.getId()) != 1)
                throw new BusinessException(ErrorCode.PARAM_ERROR, "点赞状态已变化，请刷新后重试");
            strategy.decrement(request.getTargetId());
        }
        events.publishEvent(new LikeChanged(actor, request.getTargetId(), strategy.type(), snapshot, liked));
        return LikeResponseDto.builder().isLiked(liked)
                .likeCount(Math.max(0, snapshot.count() + (liked ? 1 : -1))).build();
    }
}
