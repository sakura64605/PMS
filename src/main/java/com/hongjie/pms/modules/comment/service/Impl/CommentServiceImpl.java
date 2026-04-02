package com.hongjie.pms.modules.comment.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.CommentLikeTypes;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.comment.dto.request.CommentCreateRequest;
import com.hongjie.pms.modules.comment.dto.response.CommentRespDto;
import com.hongjie.pms.modules.comment.entity.Comment;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.comment.service.CommentService;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final PetPostMapper petPostMapper;
    private final ActivityMapper activityMapper;
    private final MessageService messageService;

    @Override
    public void createComment(CommentCreateRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            log.error("用户未登录");
            throw new RuntimeException("用户未登录");
        }
        log.debug("用户创建评论: userId={}, username={}", userId, UserContext.getUserName());
        if (request.getContent() == null || request.getContent().isEmpty()) {
            log.error("评论内容为空");
            throw new RuntimeException("评论内容为空");
        }
        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTargetType(request.getTargetType());
        comment.setTargetId(request.getTargetId());
        comment.setContent(request.getContent());
        comment.setParentId(request.getParentId());
        comment.setReplyTo(request.getReplyTo());
        comment.setStatus(1);
        log.debug("保存评论: {}", comment);
        commentMapper.insert(comment);
        if (request.getTargetType().equals(CommentLikeTypes.PET_POST)){
            petPostMapper.incrementCommentCount(request.getTargetId());
        } else if (request.getTargetType().equals(CommentLikeTypes.PET_ACTIVITY)){
            activityMapper.incrementCommentCount(request.getTargetId());
        }
        log.info("保存评论成功: {}", comment);

        // 增加评论计数
        if (request.getTargetType().equals(CommentLikeTypes.PET_POST)) {
            petPostMapper.incrementCommentCount(request.getTargetId());

            // 发送评论通知
            PetPost petPost = petPostMapper.selectById(request.getTargetId());
            if (petPost != null && !userId.equals(petPost.getUserId())) {
                messageService.sendCommentNotification(
                        petPost.getUserId(),           // 接收者（帖子作者）
                        userId,                        // 发送者（评论者）
                        CommentLikeTypes.PET_POST,                    // 目标类型
                        petPost.getTitle(),            // 标题
                        request.getContent(),          // 评论内容
                        request.getTargetId(),         // 业务ID
                        "/pet/" + request.getTargetId()  // 链接
                );
            }

        } else if (request.getTargetType().equals(CommentLikeTypes.PET_ACTIVITY)) {
            activityMapper.incrementCommentCount(request.getTargetId());

            // 发送评论通知
            Activity activity = activityMapper.selectById(request.getTargetId());
            if (activity != null && !userId.equals(activity.getUserId())) {
                messageService.sendCommentNotification(
                        activity.getUserId(),          // 接收者（活动发布者）
                        userId,                        // 发送者
                        CommentLikeTypes.PET_ACTIVITY,                // 目标类型
                        activity.getTitle(),           // 标题
                        request.getContent(),          // 评论内容
                        request.getTargetId(),         // 业务ID
                        "/activity/" + request.getTargetId()  // 链接
                );
            }
        }

        // 如果是回复评论，还需要通知被回复的人
        if (request.getParentId() != null && request.getParentId() > 0) {
            Comment parentComment = commentMapper.selectById(request.getParentId());
            if (parentComment != null && !parentComment.getUserId().equals(userId)) {
                // 回复评论时
                messageService.sendCommentNotification(
                        parentComment.getUserId(),
                        userId,
                        CommentLikeTypes.COMMENT_REPLY,
                        null,
                        "回复了你的评论：" + request.getContent(),
                        request.getParentId(),
                        "/pet/" + request.getTargetId() + "?commentId=" + request.getParentId()  // 带评论锚点
                );
            }
        }

        log.info("评论创建成功: userId={}, targetType={}, targetId={}",
                userId, request.getTargetType(), request.getTargetId());

    }

    @Override
    public IPage<CommentRespDto> getCommentList(String targetType, Long targetId, Integer pageNum, Integer pageSize) {
        // 1. 查询顶级评论
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, targetType)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getStatus, 1)
                .eq(Comment::getParentId, 0)
                .orderByDesc(Comment::getCreateTime);

        Page<Comment> page = new Page<>(pageNum, pageSize);
        IPage<Comment> commentPage = commentMapper.selectPage(page, wrapper);

        if (commentPage.getRecords().isEmpty()) {
            return new Page<>();
        }

        // 2. 收集所有需要查询的ID
        List<Long> commentIds = commentPage.getRecords().stream()
                .map(Comment::getId)
                .toList();

        // 3. 递归查询所有子评论
        List<Comment> allReplies = getAllReplies(commentIds, targetType, targetId);

        // 4. 按 parentId 分组
        Map<Long, List<Comment>> repliesMap = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        // 5. 批量查询用户信息
        List<Long> allUserIds = new ArrayList<>();
        allUserIds.addAll(commentPage.getRecords().stream().map(Comment::getUserId).toList());
        allUserIds.addAll(allReplies.stream().map(Comment::getUserId).toList());
        allUserIds.addAll(allReplies.stream().map(Comment::getReplyTo).filter(Objects::nonNull).toList());
        allUserIds = allUserIds.stream().distinct().toList();

        Map<Long, UserSimpleDto> userMap = getUserMap(allUserIds);

        // 6. 递归构建响应
        List<CommentRespDto> records = commentPage.getRecords().stream()
                .map(comment -> buildCommentTree(comment, repliesMap, userMap))
                .collect(Collectors.toList());

        IPage<CommentRespDto> result = new Page<>();
        result.setRecords(records);
        result.setTotal(commentPage.getTotal());
        result.setCurrent(commentPage.getCurrent());
        result.setSize(commentPage.getSize());
        return result;
    }

    /**
     * 递归查询所有子评论
     */
    private List<Comment> getAllReplies(List<Long> parentIds, String targetType, Long targetId) {
        if (parentIds == null || parentIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, targetType)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getStatus, 1)
                .in(Comment::getParentId, parentIds)
                .orderByAsc(Comment::getCreateTime);

        List<Comment> replies = commentMapper.selectList(wrapper);

        if (replies.isEmpty()) {
            return replies;
        }

        // 递归查询更深层的回复
        List<Long> nextParentIds = replies.stream()
                .map(Comment::getId)
                .toList();
        List<Comment> deeperReplies = getAllReplies(nextParentIds, targetType, targetId);

        replies.addAll(deeperReplies);
        return replies;
    }

    /**
     * 递归构建评论树
     */
    private CommentRespDto buildCommentTree(Comment comment,
                                            Map<Long, List<Comment>> repliesMap,
                                            Map<Long, UserSimpleDto> userMap) {

        CommentRespDto dto = CommentRespDto.builder()
                .id(comment.getId())
                .user(userMap.get(comment.getUserId()))
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .createTime(comment.getCreateTime())
                .replyTo(userMap.get(comment.getReplyTo()))
                .parentId(comment.getParentId())
                .replies(new ArrayList<>())
                .build();

        // 递归构建子评论
        List<Comment> childComments = repliesMap.get(comment.getId());
        if (childComments != null && !childComments.isEmpty()) {
            List<CommentRespDto> childDtos = childComments.stream()
                    .map(child -> buildCommentTree(child, repliesMap, userMap))
                    .sorted(Comparator.comparing(CommentRespDto::getCreateTime))
                    .collect(Collectors.toList());
            dto.setReplies(childDtos);
        }

        return dto;
    }

    // 抽取查询用户方法
    private Map<Long, UserSimpleDto> getUserMap(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(
                User::getId,
                user -> UserSimpleDto.builder()
                        .userId(user.getId())
                        .username(user.getUserName())
                        .nickname(user.getNickName())
                        .avatar(user.getAvatar())
                        .build()
        ));
    }

}
