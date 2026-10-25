package com.hongjie.pms.modules.content.petpost.application.handler;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.core.exception.Guard;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.shared.extension.target.Commentable;
import com.hongjie.pms.shared.extension.target.Favorable;
import com.hongjie.pms.shared.extension.target.Likeable;
import com.hongjie.pms.shared.extension.target.Reportable;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PmsPetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PmsPetPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 宠物帖子的目标处理器 —— 一次性实现四种互动能力。
 *
 * <p>它的存在让「评论 / 点赞 / 收藏 / 举报」四条链路都不再需要
 * {@code if (targetType.equals("pet_post"))} 这类分支：调用方从注册表按
 * {@code BizTargetType.PET_POST} 取到本类，直接调能力方法。
 *
 * <p><b>读写的是新表</b>：本处理器接的是 {@link PmsPetPostMapper}（{@code pms_pet_post}），
 * 而不是老的 {@code PetPostMapper}（{@code pet_post}）。这一点很关键 ——
 * 如果评论链路还在给老表自增计数，新表里的 comment_count 就会永远是 0。
 *
 * <p><b>关于 {@code onReportAccepted}</b>：{@code Reportable} 提供了默认空实现，
 * 本类刻意不覆写。老代码在 {@code ReportServiceImpl} 里对帖子举报成立时会执行下架，
 * 但那段逻辑与通知、审核记录等副作用耦合在一起；在没有回归保障前照抄一条 UPDATE
 * 去替代它，风险大于收益。举报链路继续走老实现，待该模块重构时再统一迁移。
 */
@Component
@RequiredArgsConstructor
public class PetPostTargetHandler implements Commentable, Likeable, Favorable, Reportable {

    private final PmsPetPostMapper petPostMapper;

    @Override
    public Set<BizTargetType> supportTypes() {
        return Set.of(BizTargetType.PET_POST);
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
        return "/pet/" + targetId;
    }

    @Override
    public void incrementCommentCount(Long targetId) {
        petPostMapper.incrementCommentCount(targetId);
    }

    @Override
    public void incrementLikeCount(Long targetId) {
        petPostMapper.incrementLikeCount(targetId);
    }

    @Override
    public void decrementLikeCount(Long targetId) {
        petPostMapper.decrementLikeCount(targetId);
    }

    @Override
    public String summary(Long targetId) {
        return load(targetId).getTitle();
    }

    private PmsPetPost load(Long targetId) {
        return Guard.requireFound(petPostMapper.selectById(targetId), ErrorCode.PET_NOT_FOUND, targetId);
    }
}
