package com.hongjie.pms.modules.governance.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.platform.enums.AuditStatus;
import com.hongjie.pms.modules.governance.audit.api.dto.AuditListDto;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class AuditQuerySources {
    @Bean AuditQuerySource adoptQuery(PetPostMapper mapper) { return pet(mapper, "adopt", 0, "领养"); }
    @Bean AuditQuerySource helpQuery(PetPostMapper mapper) { return pet(mapper, "help", 1, "救助"); }
    private AuditQuerySource pet(PetPostMapper mapper, String type, int code, String description) {
        Function<PetPost, AuditQuerySource.Item> convert = row -> new AuditQuerySource.Item(row.getUserId(),
                AuditListDto.builder().id(row.getId()).targetType(type).targetTypeDesc(description)
                .title(row.getTitle()).content(shorten(row.getContent(), 100)).images(row.getImages())
                .createTime(row.getCreateTime()).auditStatus(row.getAuditStatus())
                .auditStatusDesc(AuditStatus.getDescByCode(row.getAuditStatus()))
                .petType(row.getPetType()).petName(row.getPetName()).petAge(row.getPetAge())
                .petGender(row.getPetGender()).address(row.getAddress()).contactPhone(row.getContactPhone()).build());
        return source(type, keyword -> {
            var query = new LambdaQueryWrapper<PetPost>().eq(PetPost::getType, code).eq(PetPost::getAuditStatus, AuditStatus.PENDING.getCode()).notIn(PetPost::getStatus, -1, 2);
            if (StringUtils.hasText(keyword)) query.and(q -> q.like(PetPost::getTitle, keyword).or().like(PetPost::getContent, keyword));
            return mapper.selectList(query).stream().map(convert).toList();
        }, ids -> mapper.selectBatchIds(ids).stream().filter(row -> Objects.equals(row.getType(), code)).map(convert).toList());
    }
    @Bean AuditQuerySource activityQuery(ActivityMapper mapper) {
        Function<Activity, AuditQuerySource.Item> convert = row -> new AuditQuerySource.Item(row.getUserId(),
                AuditListDto.builder().id(row.getId()).targetType("activity").targetTypeDesc("活动")
                .title(row.getTitle()).content(shorten(row.getContent(), 100)).images(row.getImages())
                .createTime(row.getCreateTime()).auditStatus(row.getAuditStatus())
                .auditStatusDesc(AuditStatus.getDescByCode(row.getAuditStatus())).location(row.getLocation())
                .startTime(row.getStartTime()).endTime(row.getEndTime()).maxPeople(row.getMaxPeople())
                .currentPeople(row.getCurrentPeople()).build());
        return source("activity", keyword -> {
            var query = new LambdaQueryWrapper<Activity>().eq(Activity::getAuditStatus, AuditStatus.PENDING.getCode()).ne(Activity::getStatus, 2);
            if (StringUtils.hasText(keyword)) query.and(q -> q.like(Activity::getTitle, keyword).or().like(Activity::getContent, keyword));
            return mapper.selectList(query).stream().map(convert).toList();
        }, ids -> mapper.selectBatchIds(ids).stream().map(convert).toList());
    }
    @Bean AuditQuerySource dailyQuery(DailyPostMapper mapper) {
        Function<DailyPost, AuditQuerySource.Item> convert = row -> new AuditQuerySource.Item(row.getUserId(),
                AuditListDto.builder().id(row.getId()).targetType("daily").targetTypeDesc("日常动态")
                .title(shorten(row.getContent(), 50)).content(row.getContent()).images(row.getImages())
                .createTime(row.getCreateTime()).auditStatus(row.getAuditStatus())
                .auditStatusDesc(AuditStatus.getDescByCode(row.getAuditStatus())).location(row.getLocation()).build());
        return source("daily", keyword -> {
            var query = new LambdaQueryWrapper<DailyPost>().eq(DailyPost::getAuditStatus, AuditStatus.PENDING.getCode()).eq(DailyPost::getStatus, 1);
            if (StringUtils.hasText(keyword)) query.and(q -> q.like(DailyPost::getContent, keyword).or().like(DailyPost::getLocation, keyword));
            return mapper.selectList(query).stream().map(convert).toList();
        }, ids -> mapper.selectBatchIds(ids).stream().map(convert).toList());
    }
    static String shorten(String value, int limit) {
        return value != null && value.length() > limit ? value.substring(0, limit) + "..." : value;
    }
    private AuditQuerySource source(String type, Function<String, List<AuditQuerySource.Item>> pending,
                                     Function<Collection<Long>, List<AuditQuerySource.Item>> lookup) {
        return new AuditQuerySource() {
            public String type() { return type; }
            public List<Item> pending(String keyword) { return pending.apply(keyword); }
            public Map<Long, Item> findAll(Collection<Long> ids) {
                if (ids.isEmpty()) return Map.of();
                return lookup.apply(ids).stream().collect(Collectors.toMap(i -> i.view().getId(), Function.identity()));
            }
        };
    }
}
