package com.hongjie.pms.modules.notice.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.annotation.DistributedCacheable;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.mq.CacheUpdateProducer;
import com.hongjie.pms.modules.notice.dto.request.NoticeRequestDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeListDto;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.entity.NoticeReadRecord;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.notice.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.notice.service.NoticeService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final NoticeReadRecordMapper noticeReadRecordMapper;
    private final CacheUpdateProducer cacheUpdateProducer;
    private final RedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    @Override
    @Transactional
    public NoticeDetailDto create(NoticeRequestDto request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Notice notice = new Notice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setType(request.getType());
        notice.setPriority(request.getPriority());
        notice.setIsTop(request.getIsTop());
        notice.setCreateBy(UserContext.getUserId());

        noticeMapper.insert(notice);

        // 判断是立即发布还是定时发布
        if (request.getSchedulePublishTime() != null) {
            // 定时发布：状态为草稿，设置定时发布时间
            notice.setStatus(0);  // 草稿
            notice.setPublishTime(request.getSchedulePublishTime());
            schedulePublish(notice.getId(), request.getSchedulePublishTime());
            log.info("管理员创建定时公告: {}, 定时时间: {}",
                    notice.getTitle(), request.getSchedulePublishTime());
        } else {
            // 立即发布：状态为已发布，发布时间为当前时间
            notice.setStatus(1);  // 已发布
            notice.setPublishTime(LocalDateTime.now());
            log.info("管理员创建并发布公告: {}", notice.getTitle());
        }


        cacheUpdateProducer.sendEvictAll("noticeList");

        return convertToDetailDto(notice, false);
    }

    /**
     * 添加定时发布任务
     */
    public void schedulePublish(Long noticeId, LocalDateTime publishTime) {
        long delay = publishTime.toEpochSecond(ZoneOffset.UTC) * 1000 - System.currentTimeMillis();

        if (delay <= 0) {
            // 已经过了发布时间，立即发布
            publishNow(noticeId);
            return;
        }

        RBlockingQueue<Long> blockingQueue = redissonClient.getBlockingQueue("notice:delay:queue");
        RDelayedQueue<Long> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

        delayedQueue.offer(noticeId, delay, TimeUnit.MILLISECONDS);

        log.info("定时发布任务已添加: noticeId={}, 将在{}ms后发布", noticeId, delay);
    }

    /**
     * 启动消费者
     */
    @PostConstruct
    public void startConsumer() {
        new Thread(() -> {
            RBlockingQueue<Long> blockingQueue = redissonClient.getBlockingQueue("notice:delay:queue");
            while (true) {
                try {
                    Long noticeId = blockingQueue.take();
                    log.info("定时发布任务触发: noticeId={}", noticeId);
                    publishNow(noticeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    private void publishNow(Long noticeId) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null || notice.getStatus() == 1) {
            return;
        }

        notice.setStatus(1);
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);

        // 清除缓存
        cacheUpdateProducer.sendEvictAll("notice:List");

        log.info("公告已定时发布: noticeId={}, title={}", noticeId, notice.getTitle());
    }

    @Override
    @Transactional
    public NoticeDetailDto update(NoticeRequestDto request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        Notice notice = noticeMapper.selectById(request.getId());
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setType(request.getType());
        notice.setPriority(request.getPriority());
        notice.setIsTop(request.getIsTop());

        if (request.getSchedulePublishTime() != null) {
            notice.setStatus(0);
            notice.setPublishTime(request.getSchedulePublishTime());
            schedulePublish(notice.getId(), request.getSchedulePublishTime());
        }

        noticeMapper.updateById(notice);

        cacheUpdateProducer.sendEvict("notice", String.valueOf(notice.getId()));
        cacheUpdateProducer.sendEvictAll("noticeList");

        log.info("管理员更新公告: {}", notice.getTitle());
        
        return convertToDetailDto(notice, false);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        
        noticeMapper.deleteById(id);

        cacheUpdateProducer.sendEvict("notice", String.valueOf(notice.getId()));
        cacheUpdateProducer.sendEvictAll("noticeList");

        log.info("管理员删除公告: {}", notice.getTitle());
    }

    @Override
    @Transactional
    public void publish(Long id) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }

        // 已发布的不能重复发布
        if (notice.getStatus() == 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "公告已发布");
        }

        // 设置发布时间为当前时间
        notice.setStatus(1);
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);

        cacheUpdateProducer.sendEvictAll("noticeList");

        log.info("管理员发布公告: {}", notice.getTitle());
    }

    @Override
    @Transactional
    public void unpublish(Long id) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        
        notice.setStatus(2);
        noticeMapper.updateById(notice);

        cacheUpdateProducer.sendEvictAll("noticeList");

        log.info("管理员下架公告: {}", notice.getTitle());
    }

    @Override
    public IPage<NoticeListDto> listForAdmin(Integer pageNum, Integer pageSize, 
                                               Integer status, String keyword) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        Page<Notice> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null) {
            wrapper.eq(Notice::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Notice::getTitle, keyword)
                    .or()
                    .like(Notice::getContent, keyword)
            );
        }
        wrapper.orderByDesc(Notice::getIsTop)
               .orderByDesc(Notice::getPriority)
               .orderByDesc(Notice::getCreateTime);
        
        IPage<Notice> noticePage = noticeMapper.selectPage(page, wrapper);
        
        List<NoticeListDto> records = noticePage.getRecords().stream()
                .map(notice -> convertToListDto(notice, null))
                .collect(Collectors.toList());
        
        Page<NoticeListDto> resultPage = new Page<>(pageNum, pageSize, noticePage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public IPage<NoticeListDto> listForUser(Integer pageNum, Integer pageSize) {
        // 1. 生成缓存key
        String cacheKey = "notice:list:" + pageNum + "_" + pageSize;

        // 2. 查缓存
        IPage<NoticeListDto> cached = (IPage<NoticeListDto>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("从缓存获取公告列表: key={}", cacheKey);
            return cached;
        }

        // 3. 查数据库
        Long userId = UserContext.getUserId();

        Page<Notice> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notice::getStatus, 1)
                .le(Notice::getPublishTime, LocalDateTime.now())
                .and(w -> w.isNull(Notice::getExpireTime)
                        .or()
                        .gt(Notice::getExpireTime, LocalDateTime.now()))
                .orderByDesc(Notice::getIsTop)
                .orderByDesc(Notice::getPriority)
                .orderByDesc(Notice::getPublishTime);

        IPage<Notice> noticePage = noticeMapper.selectPage(page, wrapper);

        // 批量查询已读状态
        List<Long> noticeIds = noticePage.getRecords().stream()
                .map(Notice::getId)
                .collect(Collectors.toList());

        Map<Long, Boolean> readStatusMap = getReadStatusMap(userId, noticeIds);

        List<NoticeListDto> records = noticePage.getRecords().stream()
                .map(notice -> convertToListDto(notice, readStatusMap.get(notice.getId())))
                .collect(Collectors.toList());

        Page<NoticeListDto> resultPage = new Page<>(pageNum, pageSize, noticePage.getTotal());
        resultPage.setRecords(records);

        // 4. 写入缓存（10分钟）
        redisTemplate.opsForValue().set(cacheKey, resultPage, 600, TimeUnit.SECONDS);

        return resultPage;
    }

    @Override
    @Transactional
    public NoticeDetailDto getByIdForUser(Long id) {
        // 1. 查缓存
        String cacheKey = "notice:" + id;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            // 兼容处理：如果缓存的是 Notice 实体，则转换并重新缓存 DTO
            if (cached instanceof Notice) {
                log.warn("缓存中发现旧格式的Notice实体，正在迁移: id={}", id);
                Notice oldNotice = (Notice) cached;
                NoticeDetailDto converted = convertToDetailDto(oldNotice,
                        noticeReadRecordMapper.exists(id, UserContext.getUserId()));
                // 更新为正确格式
                redisTemplate.opsForValue().set(cacheKey, converted, 1800, TimeUnit.SECONDS);
                return converted;
            }

            // 正常情况：直接返回 DTO
            if (cached instanceof NoticeDetailDto) {
                log.info("从缓存获取公告详情: id={}", id);
                return (NoticeDetailDto) cached;
            }

            // 其他意外类型：删除脏缓存
            log.warn("缓存中存在异常类型: id={}, type={}", id, cached.getClass().getName());
            redisTemplate.delete(cacheKey);
        }

        // 2. 查数据库
        Long userId = UserContext.getUserId();

        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }

        if (notice.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }

        if (notice.getExpireTime() != null && notice.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告已过期");
        }

        markAsRead(id, userId);

        boolean isRead = noticeReadRecordMapper.exists(id, userId);

        NoticeDetailDto dto = convertToDetailDto(notice, isRead);

        // 3. 写入缓存（30分钟）
        redisTemplate.opsForValue().set(cacheKey, dto, 1800, TimeUnit.SECONDS);

        return dto;
    }

    @Override
    public int getUnreadCount() {
        Long userId = UserContext.getUserId();
        
        // 获取所有已发布的公告
        List<Notice> publishedNotices = noticeMapper.getPublishedNotices();
        
        if (publishedNotices.isEmpty()) {
            return 0;
        }
        
        // 统计未读数量
        int unreadCount = 0;
        for (Notice notice : publishedNotices) {
            if (!noticeReadRecordMapper.exists(notice.getId(), userId)) {
                unreadCount++;
            }
        }
        
        return unreadCount;
    }
    
    /**
     * 标记公告为已读
     */
    private void markAsRead(Long noticeId, Long userId) {
        if (!noticeReadRecordMapper.exists(noticeId, userId)) {
            NoticeReadRecord record = new NoticeReadRecord();
            record.setNoticeId(noticeId);
            record.setUserId(userId);
            noticeReadRecordMapper.insert(record);
        }
    }
    
    /**
     * 批量获取已读状态
     */
    private Map<Long, Boolean> getReadStatusMap(Long userId, List<Long> noticeIds) {
        if (userId == null || noticeIds.isEmpty()) {
            return Map.of();
        }
        
        List<NoticeReadRecord> records = noticeReadRecordMapper.selectList(
                new LambdaQueryWrapper<NoticeReadRecord>()
                        .eq(NoticeReadRecord::getUserId, userId)
                        .in(NoticeReadRecord::getNoticeId, noticeIds)
        );
        
        return records.stream()
                .collect(Collectors.toMap(
                        NoticeReadRecord::getNoticeId,
                        r -> true,
                        (v1, v2) -> v1
                ));
    }
    
    private NoticeListDto convertToListDto(Notice notice, Boolean isRead) {
        return NoticeListDto.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .type(notice.getType())
                .priority(notice.getPriority())
                .status(notice.getStatus())
                .isTop(notice.getIsTop())
                .publishTime(notice.getPublishTime())
                .expireTime(notice.getExpireTime())
                .isRead(isRead != null && isRead)
                .build();
    }
    
    private NoticeDetailDto convertToDetailDto(Notice notice, Boolean isRead) {
        return NoticeDetailDto.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .type(notice.getType())
                .priority(notice.getPriority())
                .status(notice.getStatus())
                .isTop(notice.getIsTop())
                .publishTime(notice.getPublishTime())
                .expireTime(notice.getExpireTime())
                .createTime(notice.getCreateTime())
                .isRead(isRead != null && isRead)
                .build();
    }
}