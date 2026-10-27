package com.hongjie.pms.modules.activity.application.handler;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.exception.Guard;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.shared.extension.target.Commentable;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 活动的目标处理器。
 *
 * <p>放在 {@code v2} 目录下是刻意的：{@code modules/*&#47;v2} 是本次重构「可整体删除」的标记，
 * 这样回滚时不必逐个判断哪些文件是新增的。
 *
 * <p><b>为什么只实现 {@link Commentable}</b>：本次活动模块只迁移「评论」这条链路，
 * 注册表里就只提供确实实现了的能力。若将来某处调用
 * {@code registry.get(PET_ACTIVITY, Likeable.class)}，会立刻抛出
 * 「不支持 Likeable 能力」而不是悄悄走错分支 —— 这正是能力接口存在的意义。
 * 在没有对应的 v2 点赞链路之前，为活动补上 {@code Likeable} 只是投机性代码。
 */
@Component
@RequiredArgsConstructor
public class ActivityTargetHandler implements Commentable {

    private final ActivityMapper activityMapper;

    @Override
    public Set<BizTargetType> supportTypes() {
        return Set.of(BizTargetType.PET_ACTIVITY);
    }

    @Override
    public Long ownerId(Long targetId) {
        return load(targetId).getUserId();
    }

    @Override
    public String title(Long targetId) {
        return load(targetId).getTitle();
    }

    @Override
    public String detailLink(Long targetId) {
        return "/activity/" + targetId;
    }

    @Override
    public void incrementCommentCount(Long targetId) {
        activityMapper.incrementCommentCount(targetId);
    }

    private Activity load(Long targetId) {
        return Guard.requireFound(
                activityMapper.selectById(targetId), ErrorCode.ACTIVITY_NOT_FOUND, targetId);
    }
}
