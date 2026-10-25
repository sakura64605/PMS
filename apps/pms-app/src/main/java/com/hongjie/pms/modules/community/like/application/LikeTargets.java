package com.hongjie.pms.modules.community.like.application;

import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.mapper.CommentMapper;
import com.hongjie.pms.shared.core.strategy.StrategyRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Consumer;

@Configuration
public class LikeTargets {
    @Bean
    LikeTarget petLikeTarget(PetPostMapper mapper) {
        return target("pet_post", id -> {
            var row = mapper.selectById(id);
            if (row == null) throw new BusinessException(ErrorCode.PET_NOT_FOUND);
            return new LikeTarget.Snapshot(row.getUserId(), row.getTitle(), "/pet/" + id, count(row.getLikeCount()));
        }, mapper::incrementLikeCount, mapper::decrementLikeCount);
    }
    @Bean
    LikeTarget activityLikeTarget(ActivityMapper mapper) {
        return target("pet_activity", id -> {
            var row = mapper.selectById(id);
            if (row == null) throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
            return new LikeTarget.Snapshot(row.getUserId(), row.getTitle(), "/activity/" + id, count(row.getLikeCount()));
        }, mapper::incrementLikeCount, mapper::decrementLikeCount);
    }
    @Bean
    LikeTarget commentLikeTarget(CommentMapper mapper) {
        return target("pet_comment", id -> {
            var row = mapper.selectById(id);
            if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
            String prefix = switch (row.getTargetType()) {
                case "pet_post" -> "/pet/";
                case "pet_activity" -> "/activity/";
                default -> "";
            };
            String link = prefix.isEmpty() ? "" : prefix + row.getTargetId() + "?commentId=" + id;
            return new LikeTarget.Snapshot(row.getUserId(), null, link, count(row.getLikeCount()));
        }, mapper::incrementLikeCount, mapper::decrementLikeCount);
    }
    @Bean
    StrategyRegistry<String, LikeTarget> likeTargetRegistry(List<LikeTarget> targets) {
        return new StrategyRegistry<>("点赞目标", targets, t -> List.of(t.type()));
    }
    private static int count(Integer value) { return value == null ? 0 : value; }
    private static LikeTarget target(String type, Function<Long, LikeTarget.Snapshot> loader,
                                     Consumer<Long> increment, Consumer<Long> decrement) {
        return new LikeTarget() {
            public String type() { return type; }
            public Snapshot load(Long id) { return loader.apply(id); }
            public void increment(Long id) { increment.accept(id); }
            public void decrement(Long id) { decrement.accept(id); }
        };
    }
}
