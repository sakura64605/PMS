package com.hongjie.pms.modules.content.daily.application.handler;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.exception.Guard;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.shared.extension.target.Commentable;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 日常动态的目标处理器。
 *
 * <p><b>标题为什么用 content</b>：日常动态没有标题字段。老实现在评论通知里
 * 把 {@code dailyPost.getContent()} 当作「标题」传给消息服务，为避免文案行为发生变化，
 * 这里保持一致 —— 即便每天动态的正文很长，也仍由通知处理器统一按 100 字截断。
 * 若改成「取正文前 N 字」，通知文案就会与历史记录不一致，属于产品决策而非重构范畴。
 */
@Component
@RequiredArgsConstructor
public class DailyPostTargetHandler implements Commentable {

    private final DailyPostMapper dailyPostMapper;

    @Override
    public Set<BizTargetType> supportTypes() {
        return Set.of(BizTargetType.DAILY);
    }

    @Override
    public Long ownerId(Long targetId) {
        return load(targetId).getUserId();
    }

    @Override
    public String title(Long targetId) {
        return load(targetId).getContent();
    }

    @Override
    public String detailLink(Long targetId) {
        return "/daily/" + targetId;
    }

    @Override
    public void incrementCommentCount(Long targetId) {
        dailyPostMapper.incrementCommentCount(targetId);
    }

    private DailyPost load(Long targetId) {
        return Guard.requireFound(dailyPostMapper.selectById(targetId), ErrorCode.NOT_FOUND, targetId);
    }
}
