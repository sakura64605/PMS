package com.hongjie.pms.modules.governance.audit.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import org.springframework.context.annotation.*;
import java.util.function.*;

@Configuration(proxyBeanMethods = false)
public class AuditWriteTargets {
    @Bean
    AuditWriteTarget adoptWriter(AuditWriteMapper locks, PetPostMapper mapper) {
        return pet("adopt", 0, locks, mapper);
    }

    @Bean
    AuditWriteTarget helpWriter(AuditWriteMapper locks, PetPostMapper mapper) {
        return pet("help", 1, locks, mapper);
    }

    private AuditWriteTarget pet(
            String type, int code, AuditWriteMapper locks, PetPostMapper mapper) {
        return target(
                type,
                id -> locks.lockPet(id, code),
                id -> {
                    var row = mapper.selectById(id);
                    if (row == null) throw missing();
                    return new AuditWriteTarget.Snapshot(
                            row.getUserId(), row.getTitle(), "pet_post");
                },
                (id, status) ->
                        mapper.update(
                                null,
                                new LambdaUpdateWrapper<PetPost>()
                                        .eq(PetPost::getId, id)
                                        .eq(PetPost::getType, code)
                                        .eq(PetPost::getAuditStatus, 0)
                                        .set(PetPost::getAuditStatus, status)
                                        .set(PetPost::getStatus, status == 1 ? 1 : 0)));
    }

    @Bean
    AuditWriteTarget activityWriter(AuditWriteMapper locks, ActivityMapper mapper) {
        return target(
                "activity",
                locks::lockActivity,
                id -> {
                    var row = mapper.selectById(id);
                    if (row == null) throw missing();
                    return new AuditWriteTarget.Snapshot(
                            row.getUserId(), row.getTitle(), "activity");
                },
                (id, status) ->
                        mapper.update(
                                null,
                                new LambdaUpdateWrapper<Activity>()
                                        .eq(Activity::getId, id)
                                        .eq(Activity::getAuditStatus, 0)
                                        .set(Activity::getAuditStatus, status)
                                        .set(Activity::getStatus, status == 1 ? 0 : 3)));
    }

    @Bean
    AuditWriteTarget dailyWriter(AuditWriteMapper locks, DailyPostMapper mapper) {
        return target(
                "daily",
                locks::lockDaily,
                id -> {
                    var row = mapper.selectById(id);
                    if (row == null) throw missing();
                    return new AuditWriteTarget.Snapshot(
                            row.getUserId(), row.getContent(), "daily");
                },
                (id, status) ->
                        mapper.update(
                                null,
                                new LambdaUpdateWrapper<DailyPost>()
                                        .eq(DailyPost::getId, id)
                                        .eq(DailyPost::getAuditStatus, 0)
                                        .eq(DailyPost::getStatus, 1)
                                        .set(DailyPost::getAuditStatus, status)));
    }

    private AuditWriteTarget target(
            String type,
            Function<Long, Long> lock,
            Function<Long, AuditWriteTarget.Snapshot> load,
            BiFunction<Long, Integer, Integer> update) {
        return new AuditWriteTarget() {
            public String type() {
                return type;
            }

            public void lock(Long id) {
                if (lock.apply(id) == null) throw missing();
            }

            public Snapshot load(Long id) {
                return load.apply(id);
            }

            public void decide(Long id, int status) {
                if (update.apply(id, status) != 1)
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "内容审核状态已变化，请刷新后重试");
            }
        };
    }

    private static BusinessException missing() {
        return new BusinessException(ErrorCode.NOT_FOUND, "待审核内容不存在或类型不匹配");
    }
}
