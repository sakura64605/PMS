package com.hongjie.pms.modules.like.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.CommentLikeTypes;
import com.hongjie.pms.common.enums.ErrorCode;
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
import com.hongjie.pms.modules.message.service.MessageService;
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
    private final MessageService messageService;  // 新增

    @Override
    public LikeResponseDto like(LikeRequest request) {
        Long currentUserId = UserContext.getUserId();

        // 查询是否已点赞
        LikeRecord likeRecord = likeRecordMapper.selectOne(
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, currentUserId)
                        .eq(LikeRecord::getTargetId, request.getTargetId())
                        .eq(LikeRecord::getTargetType, request.getTargetType())
        );

        boolean isNewLike = (likeRecord == null);
        Integer likeCount = 0;

        switch (request.getTargetType()) {
            case CommentLikeTypes.PET_POST:
                PetPost pet = petPostMapper.selectById(request.getTargetId());
                if (pet == null) {
                    throw new BusinessException(ErrorCode.PET_NOT_FOUND);
                }

                if (isNewLike) {
                    // 新增点赞
                    petPostMapper.incrementLikeCount(request.getTargetId());
                    likeCount = pet.getLikeCount() + 1;

                    // 发送点赞通知（不是给自己点赞）
                    if (!currentUserId.equals(pet.getUserId())) {
                        messageService.sendLikeNotification(
                                pet.getUserId(),           // 接收者（帖子作者）
                                currentUserId,             // 发送者（点赞用户）
                                CommentLikeTypes.PET_POST,                // 目标类型
                                pet.getTitle(),            // 标题
                                request.getTargetId(),     // 业务ID
                                "/pet/" + request.getTargetId()  // 跳转链接
                        );
                    }
                } else {
                    // 取消点赞
                    petPostMapper.decrementLikeCount(request.getTargetId());
                    likeCount = pet.getLikeCount() - 1;
                }
                break;

            case CommentLikeTypes.PET_COMMENT:
                Comment comment = commentMapper.selectById(request.getTargetId());
                if (comment == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
                }

                if (isNewLike) {
                    commentMapper.incrementLikeCount(request.getTargetId());
                    likeCount = comment.getLikeCount() + 1;

                    if (!currentUserId.equals(comment.getUserId())) {
                        // 根据评论所属类型生成跳转链接
                        String link = "";
                        if (CommentLikeTypes.PET_POST.equals(comment.getTargetType())) {
                            link = "/pet/" + comment.getTargetId() + "?commentId=" + request.getTargetId();
                        } else if (CommentLikeTypes.PET_ACTIVITY.equals(comment.getTargetType())) {
                            link = "/activity/" + comment.getTargetId() + "?commentId=" + request.getTargetId();
                        }

                        messageService.sendLikeNotification(
                                comment.getUserId(),
                                currentUserId,
                                CommentLikeTypes.PET_COMMENT,
                                null,
                                request.getTargetId(),
                                link
                        );
                    }
                } else {
                    commentMapper.decrementLikeCount(request.getTargetId());
                    likeCount = comment.getLikeCount() - 1;
                }
                break;

            case CommentLikeTypes.PET_ACTIVITY:
                Activity activity = activityMapper.selectById(request.getTargetId());
                if (activity == null) {
                    throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
                }

                if (isNewLike) {
                    activityMapper.incrementLikeCount(request.getTargetId());
                    likeCount = activity.getLikeCount() + 1;

                    // 发送点赞通知（不是给自己点赞）
                    if (!currentUserId.equals(activity.getUserId())) {
                        messageService.sendLikeNotification(
                                activity.getUserId(),      // 接收者（活动发布者）
                                currentUserId,             // 发送者
                                CommentLikeTypes.PET_ACTIVITY,            // 目标类型
                                activity.getTitle(),       // 标题
                                request.getTargetId(),     // 业务ID
                                "/activity/" + request.getTargetId()
                        );
                    }
                } else {
                    activityMapper.decrementLikeCount(request.getTargetId());
                    likeCount = activity.getLikeCount() - 1;
                }
                break;

            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的点赞目标类型");
        }

        // 处理点赞记录
        if (isNewLike) {
            // 新增点赞记录
            likeRecordMapper.insert(LikeRecord.builder()
                    .userId(currentUserId)
                    .targetId(request.getTargetId())
                    .targetType(request.getTargetType())
                    .createTime(LocalDateTime.now())
                    .build());
        } else {
            // 删除点赞记录
            likeRecordMapper.deleteById(likeRecord);
        }

        log.info("{} 成功: targetId={}, targetType={}",
                isNewLike ? "点赞" : "取消点赞",
                request.getTargetId(),
                request.getTargetType());

        return LikeResponseDto.builder()
                .isLiked(isNewLike)
                .likeCount(likeCount)
                .build();
    }
}