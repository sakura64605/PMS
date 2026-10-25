package com.hongjie.pms.modules.communication.notice.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.shared.core.enums.biz.NoticeStatus;
import com.hongjie.pms.shared.core.exception.Guard;
import com.hongjie.pms.shared.core.page.PageBridge;
import com.hongjie.pms.shared.core.page.PageQuery;
import com.hongjie.pms.shared.core.page.PageResult;
import com.hongjie.pms.shared.core.security.RequireRole;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.scheduling.application.DelayTaskService;
import com.hongjie.pms.modules.communication.notice.api.dto.request.NoticeRequestDto;
import com.hongjie.pms.modules.communication.notice.api.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.communication.notice.api.dto.response.NoticeListDto;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.Notice;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.entity.NoticeReadRecord;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeMapper;
import com.hongjie.pms.modules.communication.notice.infrastructure.persistence.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.communication.notice.api.service.NoticeService;
import com.hongjie.pms.shared.core.event.DomainEventPublisher;
import com.hongjie.pms.modules.communication.notice.application.converter.NoticeDtoConverter;
import com.hongjie.pms.modules.communication.notice.application.task.NoticePublishExecutor;
import com.hongjie.pms.modules.communication.notice.api.event.NoticeChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 公告服务（重构后实现）。
 *
 * <p>实现原有的 {@link NoticeService} 接口、返回原有的 DTO，对外契约不变；
 * 标 {@link Primary} 后 {@code NoticeController} 无需改动（该接口在模块外零引用，
 * 类外也没有任何地方按具体类型注入实现类，已核实）。
 *
 * <p><b>相比老实现修掉的问题</b>：
 * <ol>
 *   <li><b>定时发布原本完全失效。</b> 老 {@code create} 调 {@code insert} 之后
 *       只修改了内存对象的 {@code status}/{@code publishTime}，<b>从未 updateById</b>。
 *       而 {@code notice.status} 的 DDL 默认值是 1（已发布），
 *       {@code publish_time} 默认 NULL —— 于是一条"定时公告"实际入库就是"已发布且无发布时间"，
 *       <b>立刻对用户可见</b>；而返回给管理员的 DTO 取自内存，显示为"草稿"。
 *       到点后 {@code publishNow} 又以 {@code status == 1} 提前返回，什么也不做。
 *       现在会把草稿状态与定时发布时间真正落库，并写入 {@code delay_task} 由调度器触发。</li>
 *   <li><b>公告列表缓存永不失效。</b> 见 {@code NoticeCacheEvictListener} 的说明 ——
 *       老代码清除的区域名与真实 key 前缀不匹配，导致增删改后用户最长 10 分钟看到旧列表。</li>
 *   <li><b>缓存清除从事务内挪到提交后</b>，消除"清完缓存、事务未提交、旧数据又被读回缓存"的窗口。</li>
 *   <li><b>{@code getUnreadCount} 的 N+1</b>：老实现对每条已发布公告单独查一次已读记录，
 *       现在是两条查询。</li>
 *   <li><b>权限校验由 6 处重复的 if 收敛为 {@code @RequireRole}</b>。</li>
 * </ol>
 *
 * <p><b>缓存 key 与格式刻意与老实现保持一致</b>（列表 {@code notice:list:{page}_{size}}、
 * 详情 {@code notice:{id}}，TTL 分别为 600s / 1800s），这样新老实现混跑期间共用同一份缓存，
 * 不会出现两套缓存各自为政。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class PmsNoticeServiceImpl implements NoticeService {

    private static final long LIST_CACHE_SECONDS = 600L;
    private static final long DETAIL_CACHE_SECONDS = 1800L;

    private final NoticeMapper noticeMapper;
    private final NoticeReadRecordMapper noticeReadRecordMapper;
    private final NoticeDtoConverter converter;
    private final DomainEventPublisher eventPublisher;
    private final DelayTaskService delayTaskService;

    @SuppressWarnings("rawtypes")
    private final RedisTemplate redisTemplate;

    // ==================== 管理员 ====================

    @Override
    @RequireRole
    @Transactional(rollbackFor = Exception.class)
    public NoticeDetailDto create(NoticeRequestDto request) {
        Notice notice = new Notice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setType(request.getType());
        notice.setPriority(request.getPriority());
        notice.setIsTop(request.getIsTop());
        notice.setCreateBy(UserContext.getUserId());

        // 定时发布时间与状态<b>必须在 insert 前设好</b>，否则会落成 DDL 默认值（已发布）
        LocalDateTime scheduleAt = request.getSchedulePublishTime();
        if (scheduleAt != null) {
            notice.setStatus(NoticeStatus.DRAFT.getCode());
            notice.setPublishTime(scheduleAt);
        } else {
            notice.setStatus(NoticeStatus.PUBLISHED.getCode());
            notice.setPublishTime(LocalDateTime.now());
        }

        noticeMapper.insert(notice);

        if (scheduleAt != null) {
            delayTaskService.addTask(NoticePublishExecutor.TASK_TYPE, notice.getId(), scheduleAt);
            log.info("管理员创建定时公告: id={}, title={}, 定时于 {}",
                    notice.getId(), notice.getTitle(), scheduleAt);
        } else {
            log.info("管理员创建并发布公告: id={}, title={}", notice.getId(), notice.getTitle());
        }

        eventPublisher.publish(new NoticeChangedEvent(notice.getId()));
        return converter.toDetailDto(notice, false);
    }

    @Override
    @RequireRole
    @Transactional(rollbackFor = Exception.class)
    public NoticeDetailDto update(NoticeRequestDto request) {
        Notice notice = requireNotice(request.getId());

        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setType(request.getType());
        notice.setPriority(request.getPriority());
        notice.setIsTop(request.getIsTop());

        LocalDateTime scheduleAt = request.getSchedulePublishTime();
        if (scheduleAt != null) {
            notice.setStatus(NoticeStatus.DRAFT.getCode());
            notice.setPublishTime(scheduleAt);
        }

        noticeMapper.updateById(notice);

        if (scheduleAt != null) {
            delayTaskService.addTask(NoticePublishExecutor.TASK_TYPE, notice.getId(), scheduleAt);
        }

        eventPublisher.publish(new NoticeChangedEvent(notice.getId()));
        log.info("管理员更新公告: id={}, title={}", notice.getId(), notice.getTitle());
        return converter.toDetailDto(notice, false);
    }

    @Override
    @RequireRole
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Notice notice = requireNotice(id);
        // notice 表有 @TableLogic deleted，deleteById 即逻辑删除
        noticeMapper.deleteById(id);

        eventPublisher.publish(new NoticeChangedEvent(id));
        log.info("管理员删除公告: id={}, title={}", id, notice.getTitle());
    }

    @Override
    @RequireRole
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Notice notice = requireNotice(id);

        if (NoticeStatus.PUBLISHED.getCode().equals(notice.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "公告已发布");
        }

        notice.setStatus(NoticeStatus.PUBLISHED.getCode());
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);

        eventPublisher.publish(new NoticeChangedEvent(id));
        log.info("管理员发布公告: id={}, title={}", id, notice.getTitle());
    }

    @Override
    @RequireRole
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        Notice notice = requireNotice(id);

        notice.setStatus(NoticeStatus.OFFLINE.getCode());
        noticeMapper.updateById(notice);

        eventPublisher.publish(new NoticeChangedEvent(id));
        log.info("管理员下架公告: id={}, title={}", id, notice.getTitle());
    }

    @Override
    @RequireRole
    public IPage<NoticeListDto> listForAdmin(Integer pageNum, Integer pageSize,
                                             Integer status, String keyword) {
        PageQuery pageQuery = PageQuery.of(asLong(pageNum), asLong(pageSize));

        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Notice::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword;
            wrapper.and(w -> w.like(Notice::getTitle, kw).or().like(Notice::getContent, kw));
        }
        wrapper.orderByDesc(Notice::getIsTop)
                .orderByDesc(Notice::getPriority)
                .orderByDesc(Notice::getCreateTime);

        IPage<Notice> page = noticeMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageBridge.toIPage(PageBridge.of(page)
                .map(notice -> converter.toListDto(notice, null)));
    }

    // ==================== 用户端 ====================

    @Override
    @SuppressWarnings("unchecked")
    public IPage<NoticeListDto> listForUser(Integer pageNum, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(asLong(pageNum), asLong(pageSize));
        String cacheKey = listCacheKey(pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize());

        IPage<NoticeListDto> cached = (IPage<NoticeListDto>) redisTemplate.opsForValue().get(cacheKey);
        LocalDateTime now = LocalDateTime.now();
        // Expiry needs no write event: refill the whole page to preserve totals/pagination.
        if (cached != null && cached.getRecords().stream().noneMatch(row ->
                !NoticeStatus.PUBLISHED.getCode().equals(row.getStatus())
                        || (row.getPublishTime() != null && row.getPublishTime().isAfter(now))
                        || (row.getExpireTime() != null && !row.getExpireTime().isAfter(now)))) {
            log.debug("从缓存获取公告列表: key={}", cacheKey);
            return forCurrentViewer(cached);
        }

        IPage<Notice> page = noticeMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<Notice>()
                        .eq(Notice::getStatus, NoticeStatus.PUBLISHED.getCode())
                        .le(Notice::getPublishTime, now)
                        .and(w -> w.isNull(Notice::getExpireTime).or().gt(Notice::getExpireTime, now))
                        .orderByDesc(Notice::getIsTop)
                        .orderByDesc(Notice::getPriority)
                        .orderByDesc(Notice::getPublishTime));

        PageResult<NoticeListDto> result = PageBridge.of(page)
                .map(notice -> converter.toListDto(notice, null));

        IPage<NoticeListDto> resultPage = PageBridge.toIPage(result);
        redisTemplate.opsForValue().set(cacheKey, resultPage, LIST_CACHE_SECONDS, TimeUnit.SECONDS);
        return forCurrentViewer(resultPage);
    }

    /** Copy even legacy personalized cache entries; never mutate shared DTOs/pages. */
    private IPage<NoticeListDto> forCurrentViewer(IPage<NoticeListDto> base) {
        Map<Long, Boolean> readStatus = readStatusMap(UserContext.getUserId(),
                base.getRecords().stream().map(NoticeListDto::getId).toList());
        Page<NoticeListDto> response = new Page<>(base.getCurrent(), base.getSize(), base.getTotal());
        response.setRecords(base.getRecords().stream().map(row -> {
            NoticeListDto copy = new NoticeListDto();
            BeanUtils.copyProperties(row, copy);
            copy.setIsRead(readStatus.get(row.getId()));
            return copy;
        }).toList());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoticeDetailDto getByIdForUser(Long id) {
        String cacheKey = detailCacheKey(id);

        Notice notice = requireNotice(id);
        // 未发布的公告对普通用户等同于不存在
        Guard.requireTrue(NoticeStatus.PUBLISHED.getCode().equals(notice.getStatus()),
                ErrorCode.NOT_FOUND, "公告不存在: " + id);
        if (notice.getExpireTime() != null && notice.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告已过期");
        }

        Long userId = UserContext.getUserId();
        markAsRead(id, userId);

        // Validate and track every read using the fresh row. Legacy shared DTOs may
        // contain another viewer's read flag, so never return them to this viewer.
        NoticeDetailDto neutral = converter.toDetailDto(notice, null);
        redisTemplate.opsForValue().set(cacheKey, neutral, DETAIL_CACHE_SECONDS, TimeUnit.SECONDS);
        return converter.toDetailDto(notice, userId != null);
    }

    @Override
    public int getUnreadCount() {
        List<Notice> published = noticeMapper.getPublishedNotices();
        if (published.isEmpty()) {
            return 0;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            return published.size();
        }

        // 老实现对每条公告单独查一次已读记录（N+1）。这里一次查出该用户在这批公告里的
        // 已读集合，再用集合判断 —— 结果与逐条 exists 完全等价，但只需一条查询。
        List<Long> noticeIds = published.stream().map(Notice::getId).toList();
        Set<Long> readNoticeIds = noticeReadRecordMapper.selectList(
                        new LambdaQueryWrapper<NoticeReadRecord>()
                                .eq(NoticeReadRecord::getUserId, userId)
                                .in(NoticeReadRecord::getNoticeId, noticeIds))
                .stream()
                .map(NoticeReadRecord::getNoticeId)
                .collect(Collectors.toSet());

        return (int) published.stream()
                .filter(notice -> !readNoticeIds.contains(notice.getId()))
                .count();
    }

    // ==================== 内部方法 ====================

    private Notice requireNotice(Long id) {
        return Guard.requireFound(noticeMapper.selectById(id), ErrorCode.NOT_FOUND, "公告不存在: " + id);
    }

    /**
     * 标记已读。
     *
     * <p><b>未登录时直接返回，不写记录</b> —— 老实现没有这个判断：
     * {@code markAsRead} 里先 {@code exists(noticeId, null)}，因为
     * {@code user_id = NULL} 永远匹配不上而返回 false，接着就会
     * <b>插入一条 user_id 为 NULL 的已读记录</b>。匿名访问详情会持续往
     * {@code notice_read_record} 里灌入 user_id 为空的垃圾行。
     */
    private void markAsRead(Long noticeId, Long userId) {
        if (userId == null) {
            return;
        }
        if (!noticeReadRecordMapper.exists(noticeId, userId)) {
            NoticeReadRecord record = new NoticeReadRecord();
            record.setNoticeId(noticeId);
            record.setUserId(userId);
            noticeReadRecordMapper.insert(record);
        }
    }

    /** 批量已读状态：一条查询即可，未登录返回空表（读状态保持 null） */
    private Map<Long, Boolean> readStatusMap(Long userId, List<Long> noticeIds) {
        if (userId == null || noticeIds.isEmpty()) {
            return Map.of();
        }
        return noticeReadRecordMapper.selectList(new LambdaQueryWrapper<NoticeReadRecord>()
                        .eq(NoticeReadRecord::getUserId, userId)
                        .in(NoticeReadRecord::getNoticeId, noticeIds))
                .stream()
                .collect(Collectors.toMap(NoticeReadRecord::getNoticeId,
                        record -> Boolean.TRUE, (a, b) -> a));
    }

    /** 与老实现一致的 key：{@code notice:list:{page}_{size}} */
    private String listCacheKey(long pageNum, long pageSize) {
        return "notice:list:" + pageNum + "_" + pageSize;
    }

    /** 与老实现一致的 key：{@code notice:{id}} */
    private String detailCacheKey(Long id) {
        return "notice:" + id;
    }

    private static Long asLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
