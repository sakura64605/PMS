package com.hongjie.pms.modules.like.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.CommentLikeTypes;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.comment.entity.Comment;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.like.dto.request.LikeRequest;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.like.service.LikeService;
import com.hongjie.pms.modules.like.dto.response.LikeResponseDto;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final PetPostMapper petPostMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final CommentMapper commentMapper;
    private final ActivityMapper activityMapper;

    @Override
    public LikeResponseDto like(LikeRequest request) {
        LikeRecord likeRecord = likeRecordMapper.selectOne(new QueryWrapper<LikeRecord>()
                .eq("user_id", UserContext.getUserId())
                .eq("target_id", request.getTargetId())
                .eq("target_type", request.getTargetType()));
        Integer count = 0;
        switch (request.getTargetType()) {
            case CommentLikeTypes.PET_POST:
                PetPost pet = petPostMapper.selectById(request.getTargetId());
                if (pet == null) {
                    throw new BusinessException(404, "宠物信息不存在");
                }
                if (likeRecord != null) {
                    pet.setLikeCount(pet.getLikeCount() - 1);
                    likeRecordMapper.deleteById(likeRecord);
                    petPostMapper.decrementLikeCount(request.getTargetId());
                    log.info("取消点赞成功: {}", pet.getLikeCount());
                    return LikeResponseDto.builder()
                            .isLiked(false)
                            .likeCount(pet.getLikeCount())
                            .build();
                }
                pet.setLikeCount(pet.getLikeCount() + 1);
                petPostMapper.incrementLikeCount(request.getTargetId());
                count = pet.getLikeCount();
                break;
            case CommentLikeTypes.PET_COMMENT:
                Comment comment = commentMapper.selectById(request.getTargetId());
                if (comment == null) {
                    throw new BusinessException(404, "评论信息不存在");
                }
                if (likeRecord != null) {
                    comment.setLikeCount(comment.getLikeCount() - 1);
                    likeRecordMapper.deleteById(likeRecord);
                    petPostMapper.decrementLikeCount(request.getTargetId());
                    log.info("取消点赞成功: {}", comment.getLikeCount());
                    return LikeResponseDto.builder()
                            .isLiked(false)
                            .likeCount(comment.getLikeCount())
                            .build();
                }
                comment.setLikeCount(comment.getLikeCount() + 1);
                petPostMapper.incrementLikeCount(request.getTargetId());
                count = comment.getLikeCount();
                break;
            case CommentLikeTypes.PET_ACTIVITY:
                Activity activity = activityMapper.selectById(request.getTargetId());
                if (activity == null) {
                    throw new BusinessException(404, "活动信息不存在");
                }
                if (likeRecord != null) {
                    activity.setLikeCount(activity.getLikeCount() - 1);
                    likeRecordMapper.deleteById(likeRecord);
                    petPostMapper.decrementLikeCount(request.getTargetId());
                    log.info("取消点赞成功: {}", activity.getLikeCount());
                    return LikeResponseDto.builder()
                            .isLiked(false)
                            .likeCount(activity.getLikeCount())
                            .build();
                }
                activity.setLikeCount(activity.getLikeCount() + 1);
                petPostMapper.incrementLikeCount(request.getTargetId());
                count = activity.getLikeCount();
                break;
            default:
                throw new BusinessException(400, "不支持的点赞目标类型");
        }


        likeRecordMapper.insert(LikeRecord.builder()
                .userId(UserContext.getUserId())
                .targetId(request.getTargetId())
                .targetType(request.getTargetType())
                .createTime(LocalDateTime.now())
                .build());

        log.info("点赞成功");
        return LikeResponseDto.builder()
                .isLiked(true)
                .likeCount(count)
                .build();
    }

}
