package com.hongjie.pms.modules.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.CommentLikeTypes;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.activity.api.dto.request.ActivityListRequestDto;
import com.hongjie.pms.modules.activity.api.dto.response.*;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.entity.Follow;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.mapper.FollowMapper;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.entity.LikeRecord;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.mapper.LikeRecordMapper;
import com.hongjie.pms.shared.core.page.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.stream.Collectors;

/** Personalized results are assembled per request and never stored in a shared response cache. */
@Service
@RequiredArgsConstructor
public class ActivityQueries {
    private final ActivityMapper activities;
    private final ActivitySignupMapper signups;
    private final LikeRecordMapper likes;
    private final FollowMapper follows;
    private final ActivityDtoAssembler assembler;

    public IPage<ActivityListRespDto> list(ActivityListRequestDto input) {
        return page(input, false);
    }
    public IPage<ActivityListRespDto> recycle(ActivityListRequestDto input) {
        requireActor();
        return page(input, true);
    }
    private IPage<ActivityListRespDto> page(ActivityListRequestDto input, boolean recycle) {
        ActivityListRequestDto request = input == null ? new ActivityListRequestDto() : input;
        Long actor = UserContext.getUserId();
        boolean own = recycle || actor != null && actor.equals(request.getUserId());
        LambdaQueryWrapper<Activity> query = new LambdaQueryWrapper<Activity>().eq(Activity::getDeleted, recycle ? 1 : 0);
        if (recycle) query.eq(Activity::getUserId, actor);
        else if (request.getUserId() != null) query.eq(Activity::getUserId, request.getUserId());
        if (!own) query.eq(Activity::getAuditStatus, 1).in(Activity::getStatus, List.of(0, 1, 2));
        else if (request.getAuditStatus() != null) query.eq(Activity::getAuditStatus, request.getAuditStatus());
        if (request.getStatus() != null) query.eq(Activity::getStatus, request.getStatus());
        if (request.getStartDate() != null) query.ge(Activity::getStartTime, request.getStartDate());
        if (request.getEndDate() != null) query.le(Activity::getStartTime, request.getEndDate());
        if (request.getStartDate() != null && request.getEndDate() != null && request.getStartDate().isAfter(request.getEndDate()))
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        if (StringUtils.hasText(request.getLocation())) query.like(Activity::getLocation, request.getLocation());
        if (StringUtils.hasText(request.getKeyword()))
            query.and(part -> part.like(Activity::getTitle, request.getKeyword()).or().like(Activity::getContent, request.getKeyword()));
        boolean asc = "asc".equalsIgnoreCase(request.getOrder());
        query.orderBy(true, asc, "startTime".equals(request.getOrderBy()) ? Activity::getStartTime : Activity::getCreateTime)
                .orderBy(true, asc, Activity::getId);
        IPage<Activity> rows = activities.selectPage(paging(request.getPageNum(), request.getPageSize()).toPage(), query);
        if (rows.getRecords().isEmpty()) return rows.convert(row -> null);
        var authors = assembler.users(rows.getRecords().stream().map(Activity::getUserId).toList());
        Set<Long> signed = signed(actor, rows.getRecords().stream().map(Activity::getId).toList());
        return rows.convert(row -> assembler.list(row, assembler.user(authors, row.getUserId()), signed.contains(row.getId())));
    }

    @Transactional
    public ActivityDetailRespDto detail(Long id) {
        requireId(id);
        Activity row = activities.selectById(id);
        if (row == null || !Objects.equals(row.getDeleted(), 0)) throw new BusinessException(ErrorCode.NOT_FOUND);
        Long actor = UserContext.getUserId();
        boolean publicContent = Objects.equals(row.getAuditStatus(), 1) && Set.of(0, 1, 2).contains(Objects.requireNonNullElse(row.getStatus(), -1));
        boolean privileged = actor != null && (actor.equals(row.getUserId()) || UserContext.isAdmin());
        if (!publicContent && !privileged) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (publicContent && actor != null && !actor.equals(row.getUserId())) {
            int changed = activities.update(null, new LambdaUpdateWrapper<Activity>().eq(Activity::getId, id)
                    .eq(Activity::getDeleted, 0).eq(Activity::getAuditStatus, 1).in(Activity::getStatus, List.of(0, 1, 2))
                    .setSql("view_count = COALESCE(view_count, 0) + 1"));
            if (changed != 1) throw new BusinessException(ErrorCode.NOT_FOUND);
            row.setViewCount(Objects.requireNonNullElse(row.getViewCount(), 0) + 1);
        }
        var author = assembler.user(assembler.users(List.of(row.getUserId())), row.getUserId());
        author.setIsFollow(actor != null && follows.exists(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, actor).eq(Follow::getFollowingId, row.getUserId())));
        boolean liked = actor != null && likes.exists(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, actor).eq(LikeRecord::getTargetId, id).eq(LikeRecord::getTargetType, CommentLikeTypes.PET_ACTIVITY));
        return assembler.detail(row, author, signed(actor, List.of(id)).contains(id), liked);
    }

    public IPage<SignUpResponse> signupList(Long id, int number, int size) {
        Long actor = requireActor();
        requireId(id);
        Activity row = activities.selectById(id);
        // Phone numbers and real names remain restricted to the organizer, including for administrators.
        if (row == null || !Objects.equals(row.getDeleted(), 0) || !actor.equals(row.getUserId()))
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        IPage<ActivitySignup> page = signups.selectPage(paging(number, size).toPage(),
                new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getActivityId, id)
                        .orderByDesc(ActivitySignup::getCreateTime).orderByDesc(ActivitySignup::getId));
        if (page.getRecords().isEmpty()) return page.convert(signup -> null);
        var users = assembler.users(page.getRecords().stream().map(ActivitySignup::getUserId).toList());
        return page.convert(signup -> assembler.signup(signup, assembler.user(users, signup.getUserId())));
    }
    private Set<Long> signed(Long actor, List<Long> ids) {
        if (actor == null || ids.isEmpty()) return Set.of();
        return signups.selectList(new LambdaQueryWrapper<ActivitySignup>().eq(ActivitySignup::getUserId, actor)
                .in(ActivitySignup::getActivityId, ids).in(ActivitySignup::getStatus, List.of(1, 3)))
                .stream().map(ActivitySignup::getActivityId).collect(Collectors.toSet());
    }
    private PageQuery paging(Integer number, Integer size) {
        return PageQuery.of(number == null ? null : number.longValue(), size == null ? null : size.longValue());
    }
    private Long requireActor() {
        Long id = UserContext.getUserId();
        if (id == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }
    private void requireId(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
    }
}
