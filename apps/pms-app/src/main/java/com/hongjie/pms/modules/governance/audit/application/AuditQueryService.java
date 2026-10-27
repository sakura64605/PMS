package com.hongjie.pms.modules.governance.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.platform.enums.AuditStatus;
import com.hongjie.pms.modules.governance.audit.api.dto.*;
import com.hongjie.pms.modules.governance.audit.infrastructure.persistence.entity.AuditRecord;
import com.hongjie.pms.modules.governance.audit.infrastructure.persistence.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.page.PageQuery;
import com.hongjie.pms.shared.core.strategy.StrategyRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuditQueryService {
    private final StrategyRegistry<String, AuditQuerySource> sources;
    private final AuditRecordMapper records;
    private final UserMapper users;

    public AuditQueryService(
            List<AuditQuerySource> sources, AuditRecordMapper records, UserMapper users) {
        this.sources = new StrategyRegistry<>("审核查询", sources, s -> List.of(s.type()));
        this.records = records;
        this.users = users;
    }

    public IPage<AuditListDto> pending(
            String type, String keyword, String range, Integer number, Integer size) {
        Collection<AuditQuerySource> selected =
                StringUtils.hasText(type) ? List.of(sources.get(type)) : sources.all();
        LocalDateTime since = startOfRange(range);
        List<AuditQuerySource.Item> items =
                selected.stream()
                        .flatMap(s -> s.pending(keyword).stream())
                        .filter(
                                i ->
                                        since == null
                                                || i.view().getCreateTime() != null
                                                        && i.view().getCreateTime().isAfter(since))
                        .sorted(
                                Comparator.comparing(
                                                (AuditQuerySource.Item i) ->
                                                        i.view().getCreateTime(),
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparing(i -> i.view().getTargetType())
                                        .thenComparing(
                                                i -> i.view().getId(), Comparator.reverseOrder()))
                        .toList();
        var page = slice(items, number, size);
        Map<Long, User> authors =
                loadUsers(page.getRecords().stream().map(AuditQuerySource.Item::ownerId).toList());
        Page<AuditListDto> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(
                page.getRecords().stream()
                        .map(
                                i -> {
                                    i.view()
                                            .setUser(
                                                    userView(
                                                            i.ownerId() == null
                                                                    ? null
                                                                    : authors.get(i.ownerId())));
                                    return i.view();
                                })
                        .toList());
        return result;
    }

    public IPage<AuditHistoryDto> history(
            String type, String keyword, Integer status, Integer number, Integer size) {
        if (StringUtils.hasText(type)) sources.get(type);
        var query = new LambdaQueryWrapper<AuditRecord>();
        if (StringUtils.hasText(type)) query.eq(AuditRecord::getTargetType, type);
        if (status != null) query.eq(AuditRecord::getAuditStatus, status);
        query.orderByDesc(AuditRecord::getCreateTime).orderByDesc(AuditRecord::getId);
        var all = records.selectList(query);
        Map<String, Map<Long, AuditQuerySource.Item>> targets = new HashMap<>();
        all.stream()
                .collect(Collectors.groupingBy(AuditRecord::getTargetType))
                .forEach(
                        (key, group) ->
                                sources.find(key)
                                        .ifPresent(
                                                source ->
                                                        targets.put(
                                                                key,
                                                                source.findAll(
                                                                        group.stream()
                                                                                .map(
                                                                                        AuditRecord
                                                                                                ::getTargetId)
                                                                                .distinct()
                                                                                .toList()))));
        var matching =
                all.stream()
                        .filter(
                                r -> {
                                    var item =
                                            targets.getOrDefault(r.getTargetType(), Map.of())
                                                    .get(r.getTargetId());
                                    return item != null
                                            && item.historyTitle() != null
                                            && (!StringUtils.hasText(keyword)
                                                    || item.historyTitle().contains(keyword));
                                })
                        .toList();
        var page = slice(matching, number, size);
        Set<Long> userIds = new HashSet<>();
        for (var row : page.getRecords()) {
            userIds.add(targets.get(row.getTargetType()).get(row.getTargetId()).ownerId());
            userIds.add(row.getAuditorId());
        }
        var userMap = loadUsers(userIds);
        Page<AuditHistoryDto> result =
                new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(
                page.getRecords().stream()
                        .map(
                                row -> {
                                    var item =
                                            targets.get(row.getTargetType()).get(row.getTargetId());
                                    var auditor =
                                            row.getAuditorId() == null
                                                    ? null
                                                    : userMap.get(row.getAuditorId());
                                    return AuditHistoryDto.builder()
                                            .id(row.getId())
                                            .targetId(row.getTargetId())
                                            .targetType(row.getTargetType())
                                            .targetTypeDesc(item.view().getTargetTypeDesc())
                                            .title(item.historyTitle())
                                            .user(
                                                    userView(
                                                            item.ownerId() == null
                                                                    ? null
                                                                    : userMap.get(item.ownerId())))
                                            .auditStatus(row.getAuditStatus())
                                            .auditStatusDesc(
                                                    AuditStatus.getDescByCode(row.getAuditStatus()))
                                            .rejectReason(row.getRejectReason())
                                            .auditorName(
                                                    auditor == null ? null : auditor.getUserName())
                                            .auditTime(row.getAuditTime())
                                            .createTime(row.getCreateTime())
                                            .build();
                                })
                        .toList());
        return result;
    }

    static <T> Page<T> slice(List<T> rows, Integer number, Integer size) {
        var query =
                PageQuery.of(
                        number == null ? null : number.longValue(),
                        size == null ? null : size.longValue());
        Page<T> page = query.toPage();
        page.setTotal(rows.size());
        long start = (page.getCurrent() - 1) * page.getSize();
        page.setRecords(
                start >= rows.size()
                        ? List.of()
                        : rows.subList(
                                (int) start, (int) Math.min(start + page.getSize(), rows.size())));
        return page;
    }

    private Map<Long, User> loadUsers(Collection<Long> ids) {
        var wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        return wanted.isEmpty()
                ? Map.of()
                : users.selectBatchIds(wanted).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private UserSimpleDto userView(User user) {
        return user == null
                ? null
                : UserSimpleDto.builder()
                        .userId(user.getId())
                        .username(user.getUserName())
                        .nickname(user.getNickName())
                        .avatar(user.getAvatar())
                        .build();
    }

    private LocalDateTime startOfRange(String range) {
        if (range == null) return null;
        var now = LocalDateTime.now();
        return switch (range) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "week" -> now.minusDays(7);
            case "month" -> now.minusMonths(1);
            default -> null;
        };
    }
}
