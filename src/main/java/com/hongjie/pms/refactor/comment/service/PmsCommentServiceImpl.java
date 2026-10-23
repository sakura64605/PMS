package com.hongjie.pms.refactor.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.circuitbreaker.annotation.CircuitBreaker;
import com.hongjie.pms.refactor.core.event.DomainEventPublisher;
import com.hongjie.pms.refactor.core.exception.Guard;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.core.enums.biz.CommentStatus;
import com.hongjie.pms.refactor.core.page.PageBridge;
import com.hongjie.pms.refactor.core.page.PageQuery;
import com.hongjie.pms.refactor.core.page.PageResult;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.refactor.extension.target.Commentable;
import com.hongjie.pms.refactor.extension.target.LegacyDialect;
import com.hongjie.pms.refactor.extension.target.TargetHandlerRegistry;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.modules.comment.dto.request.CommentCreateRequest;
import com.hongjie.pms.modules.comment.dto.response.CommentRespDto;
import com.hongjie.pms.modules.comment.entity.Comment;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.comment.service.CommentService;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.comment.converter.CommentDtoConverter;
import com.hongjie.pms.refactor.comment.converter.CommentTreeContext;
import com.hongjie.pms.refactor.comment.event.CommentNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论服务（重构后实现）。
 *
 * <p>实现原有的 {@link CommentService} 接口、返回原有的 {@link CommentRespDto}，
 * 因此对外契约不变；标 {@link Primary} 后 {@code CommentController} 无需任何改动
 * （该控制器是全项目唯一注入 {@code CommentService} 的地方）。
 *
 * <p><b>本轮不换表</b>：仍读写老的 {@code comment} 表。这也是本模块<b>没有</b>
 * 建新实体类的直接原因 —— {@code comment} 表只有 {@code create_time}、
 * 没有 {@code update_time}，无法套用 {@code BaseEntity}（它要求两列都有）。
 * 为了一条没有实际改进的实体类去改老表结构，代价大于收益。
 *
 * <p><b>相比老实现的行为改进</b>：
 * <ol>
 *   <li><b>评论数只自增一次</b>。老实现在 {@code CommentServiceImpl} 里写了两段自增分支，
 *       帖子与活动的评论数被加了两次，而日常动态只加一次。</li>
 *   <li><b>通知在事务提交后才发</b>。老实现是在事务内直接推 MQ，
 *       事务回滚时通知已经出去了，用户会收到指向不存在评论的通知。</li>
 *   <li><b>未知目标类型被明确拒绝</b>。老实现遇到无法识别的 {@code targetType}
 *       不进入任何分支：评论照常入库，但既不计数也不通知 —— 静默产生孤评论。</li>
 *   <li><b>顶级评论的 parentId 归一化为 0</b>。老实现原样存客户端传来的值，
 *       若传 null 则 {@code parent_id} 为 NULL，而列表查询用的是
 *       {@code parent_id = 0} —— 这条评论<b>永远显示不出来</b>。</li>
 *   <li><b>列表查询兼容历史字面量</b>。老实现直接用客户端传来的 {@code targetType}
 *       做等值匹配，若库里既有 {@code pet_post} 又有 {@code pet}，就只会查出一半；
 *       现在两种写法都能查到。</li>
 *   <li><b>补齐 {@code isLiked}</b>。老 {@code buildCommentTree} 建了这个字段却从未赋值，
 *       前端永远拿到 null。</li>
 *   <li>递归查询子评论加了层数上限。数据出现环时（A 的父是 B、B 的父是 A）
 *       老实现会无限递归直到栈溢出或请求超时。</li>
 * </ol>
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class PmsCommentServiceImpl implements CommentService {

    /** 顶级评论的 parentId 约定值：列表查询以 {@code parent_id = 0} 过滤顶级评论 */
    private static final Long TOP_LEVEL_PARENT_ID = 0L;

    /** 回复层级上限：正常业务远达不到，仅用于在数据异常成环时兜底 */
    private static final int MAX_REPLY_DEPTH = 20;

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final TargetHandlerRegistry targetHandlerRegistry;
    private final TargetTypeCodec targetTypeCodec;
    private final DomainEventPublisher eventPublisher;
    private final CommentDtoConverter converter;

    // ==================== 创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createComment(CommentCreateRequest request) {
        Long authorId = UserContext.getUserId();
        Guard.requireTrue(authorId != null, ErrorCode.UNAUTHORIZED, "未登录");
        String content = Guard.requireText(request.getContent(), ErrorCode.PARAM_ERROR, "评论内容不能为空");
        Guard.requireNotNull(request.getTargetId(), ErrorCode.PARAM_ERROR, "目标 ID 不能为空");
        Guard.requireText(request.getTargetType(), ErrorCode.PARAM_ERROR, "目标类型不能为空");

        // 读宽松：pet / pet_post / adopt 等历史写法都能解析
        BizTargetType targetType = BizTargetType.resolve(request.getTargetType());
        // 策略定位，无 if-else；不支持评论的目标在此明确报错（老实现会静默入库）
        Commentable target = targetHandlerRegistry.get(targetType, Commentable.class);

        Comment comment = new Comment();
        comment.setUserId(authorId);
        // 写规范码；comment 表的历史方言本就是 pet_post / pet_activity / daily，与之兼容
        comment.setTargetType(targetType.getCode());
        comment.setTargetId(request.getTargetId());
        comment.setContent(content);
        comment.setParentId(normalizeParentId(request.getParentId()));
        comment.setReplyTo(request.getReplyTo());
        comment.setStatus(CommentStatus.NORMAL.getCode());
        comment.setLikeCount(0);
        commentMapper.insert(comment);

        // 唯一的自增点
        target.incrementCommentCount(request.getTargetId());

        notifyContentOwner(comment, targetType, target, authorId);
        notifyParentCommentAuthor(comment, targetType, target, authorId);
    }

    // ==================== 查询 ====================

    @CircuitBreaker(
            value = "getCommentList",
            windowSize = 10,
            minRequestAmount = 5,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackGetCommentList"
    )
    @Override
    public IPage<CommentRespDto> getCommentList(String targetType, Long targetId,
                                                Integer pageNum, Integer pageSize) {
        Guard.requireNotNull(targetId, ErrorCode.PARAM_ERROR, "目标 ID 不能为空");
        BizTargetType type = BizTargetType.resolve(targetType);
        PageQuery pageQuery = PageQuery.of(asLong(pageNum), asLong(pageSize));

        // 1. 顶级评论分页
        IPage<Comment> commentPage = commentMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<Comment>()
                        .in(Comment::getTargetType, acceptTargetTypes(type, targetType))
                        .eq(Comment::getTargetId, targetId)
                        .eq(Comment::getStatus, CommentStatus.NORMAL.getCode())
                        .eq(Comment::getParentId, TOP_LEVEL_PARENT_ID)
                        .orderByDesc(Comment::getCreateTime));

        if (commentPage.getRecords().isEmpty()) {
            return PageBridge.toIPage(PageResult.empty(pageQuery));
        }

        // 2. 逐层取回全部子孙评论，再按 parentId 分组
        List<Comment> allReplies = loadAllReplies(commentPage.getRecords(), type, targetType, targetId, 1);
        Map<Long, List<Comment>> repliesByParent = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        // 3. 一次性查好作者/被回复人，以及当前用户的点赞集合
        CommentTreeContext context = new CommentTreeContext(
                repliesByParent,
                loadUsers(collectUserIds(commentPage.getRecords(), allReplies)),
                loadLikedCommentIds(commentPage.getRecords(), allReplies));

        // 4. 递归装配
        List<CommentRespDto> records = commentPage.getRecords().stream()
                .map(comment -> converter.toView(comment, context))
                .toList();

        return PageBridge.toIPage(PageResult.of(
                records, commentPage.getTotal(), commentPage.getCurrent(), commentPage.getSize()));
    }

    public IPage<CommentRespDto> fallbackGetCommentList(String targetType, Long targetId,
                                                        Integer pageNum, Integer pageSize) {
        log.warn("评论列表熔断降级: targetType={}, targetId={}", targetType, targetId);
        return PageBridge.toIPage(PageResult.empty(
                PageQuery.of(asLong(pageNum), asLong(pageSize))));
    }

    public IPage<CommentRespDto> fallbackGetCommentList(String targetType, Long targetId,
                                                        Integer pageNum, Integer pageSize, Exception e) {
        log.error("评论列表熔断降级: error={}", e.getMessage());
        return fallbackGetCommentList(targetType, targetId, pageNum, pageSize);
    }

    // ==================== 通知 ====================

    /** 通知被评论内容的作者（自己评论自己的内容不发） */
    private void notifyContentOwner(Comment comment, BizTargetType targetType,
                                    Commentable target, Long authorId) {
        Long ownerId = target.ownerId(comment.getTargetId());
        if (ownerId == null || ownerId.equals(authorId)) {
            return;
        }
        eventPublisher.publish(new CommentNotificationEvent(
                comment.getId(),
                authorId,
                targetType,
                comment.getTargetId(),
                // businessId 取帖子 id —— 与老实现一致
                comment.getTargetId(),
                ownerId,
                target.title(comment.getTargetId()),
                comment.getContent(),
                target.detailLink(comment.getTargetId())));
    }

    /**
     * 回复某条评论时，额外通知被回复的人。
     *
     * <p>目标类型传 {@code PET_COMMENT}，让通知处理器落到「有人评论了你」这一分支 ——
     * 与老实现把 {@code comment_reply} 兜进 {@code default} 的行为一致。
     */
    private void notifyParentCommentAuthor(Comment comment, BizTargetType targetType,
                                           Commentable target, Long authorId) {
        Long parentId = comment.getParentId();
        if (parentId == null || parentId.equals(TOP_LEVEL_PARENT_ID)) {
            return;
        }
        Comment parent = commentMapper.selectById(parentId);
        if (parent == null || parent.getUserId() == null || parent.getUserId().equals(authorId)) {
            return;
        }
        String link = target.detailLink(comment.getTargetId()) + "?commentId=" + comment.getId();
        eventPublisher.publish(new CommentNotificationEvent(
                comment.getId(),
                authorId,
                BizTargetType.PET_COMMENT,
                comment.getTargetId(),
                // businessId 取被回复评论的 id —— 老实现如此，语义上可疑但保持原样，
                // 因为它会写进用户可见的消息记录，改动属于产品决策（见事件类注释）
                parentId,
                parent.getUserId(),
                null,
                "回复了你的评论：" + comment.getContent(),
                link));
    }

    // ==================== 内部方法 ====================

    /**
     * 顶级评论的 parentId 统一为 0。
     *
     * <p>列表查询用 {@code parent_id = 0} 过滤顶级评论，所以存 NULL 的评论
     * 在列表里永远不出现。归一化后无论前端传 null、0 还是负数，行为都一致。
     */
    private Long normalizeParentId(Long parentId) {
        return parentId == null || parentId <= 0 ? TOP_LEVEL_PARENT_ID : parentId;
    }

    /**
     * 列表查询该匹配哪些 {@code target_type} 字面量。
     *
     * <p>既包含规范码，也包含客户端本次传来的原始值 —— 因为老实现是原样写入的，
     * 库里可能存在 {@code pet} / {@code pet_post} 混用的情况，
     * 只匹配其中一个会让部分历史评论凭空消失。
     */
    private Set<String> acceptTargetTypes(BizTargetType type, String rawTargetType) {
        Set<String> accepted = new LinkedHashSet<>(
                targetTypeCodec.acceptedValues(type, LegacyDialect.INTERACTION));
        if (StringUtils.hasText(rawTargetType)) {
            accepted.add(rawTargetType.trim());
        }
        return accepted;
    }

    /** 逐层取回子孙评论；{@code depth} 上限用于在数据成环时兜底 */
    private List<Comment> loadAllReplies(List<Comment> parents, BizTargetType type,
                                         String rawTargetType, Long targetId, int depth) {
        if (parents.isEmpty()) {
            return Collections.emptyList();
        }
        if (depth > MAX_REPLY_DEPTH) {
            log.warn("评论回复层级超过 {} 层，疑似 parent_id 成环: targetId={}", MAX_REPLY_DEPTH, targetId);
            return Collections.emptyList();
        }

        List<Long> parentIds = parents.stream().map(Comment::getId).toList();
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .in(Comment::getTargetType, acceptTargetTypes(type, rawTargetType))
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getStatus, CommentStatus.NORMAL.getCode())
                .in(Comment::getParentId, parentIds)
                .orderByAsc(Comment::getCreateTime));

        if (replies.isEmpty()) {
            return Collections.emptyList();
        }

        List<Comment> all = new ArrayList<>(replies);
        all.addAll(loadAllReplies(replies, type, rawTargetType, targetId, depth + 1));
        return all;
    }

    /** 需要查用户简档的全部 ID：顶级作者 + 回复作者 + 被回复人 */
    private List<Long> collectUserIds(List<Comment> topLevel, List<Comment> replies) {
        Set<Long> ids = new LinkedHashSet<>();
        topLevel.forEach(comment -> ids.add(comment.getUserId()));
        replies.forEach(comment -> {
            ids.add(comment.getUserId());
            if (comment.getReplyTo() != null) {
                ids.add(comment.getReplyTo());
            }
        });
        ids.remove(null);
        return List.copyOf(ids);
    }

    private Map<Long, UserSimpleDto> loadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::toSimpleDto));
    }

    /** 当前用户在这批评论里点赞过哪些；未登录时返回空集（isLiked 一律为 false） */
    private Set<Long> loadLikedCommentIds(List<Comment> topLevel, List<Comment> replies) {
        Long viewerId = UserContext.getUserId();
        if (viewerId == null) {
            return Collections.emptySet();
        }
        Set<Long> commentIds = new LinkedHashSet<>();
        topLevel.forEach(comment -> commentIds.add(comment.getId()));
        replies.forEach(comment -> commentIds.add(comment.getId()));
        commentIds.remove(null);
        if (commentIds.isEmpty()) {
            return Collections.emptySet();
        }

        return likeRecordMapper.selectList(new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, viewerId)
                        .in(LikeRecord::getTargetType,
                                targetTypeCodec.acceptedValues(BizTargetType.PET_COMMENT, LegacyDialect.INTERACTION))
                        .in(LikeRecord::getTargetId, commentIds))
                .stream()
                .map(LikeRecord::getTargetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private UserSimpleDto toSimpleDto(User user) {
        return UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build();
    }

    private static Long asLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
