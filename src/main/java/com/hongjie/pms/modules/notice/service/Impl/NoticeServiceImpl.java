package com.hongjie.pms.modules.notice.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.notice.dto.request.NoticeRequestDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeListDto;
import com.hongjie.pms.modules.notice.entity.Notice;
import com.hongjie.pms.modules.notice.entity.NoticeReadRecord;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.notice.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final NoticeReadRecordMapper noticeReadRecordMapper;

    @Override
    @Transactional
    public NoticeDetailDto create(NoticeRequestDto request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权限操作");
        }

        Notice notice = new Notice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setType(request.getType());
        notice.setPriority(request.getPriority());
        notice.setIsTop(request.getIsTop());
        notice.setCreateBy(UserContext.getUserId());

        // 判断是立即发布还是定时发布
        if (request.getSchedulePublishTime() != null) {
            // 定时发布：状态为草稿，设置定时发布时间
            notice.setStatus(0);  // 草稿
            notice.setPublishTime(request.getSchedulePublishTime());
            log.info("管理员创建定时公告: {}, 定时时间: {}",
                    notice.getTitle(), request.getSchedulePublishTime());
        } else {
            // 立即发布：状态为已发布，发布时间为当前时间
            notice.setStatus(1);  // 已发布
            notice.setPublishTime(LocalDateTime.now());
            log.info("管理员创建并发布公告: {}", notice.getTitle());
        }

        noticeMapper.insert(notice);
        return convertToDetailDto(notice, false);
    }

    @Override
    @Transactional
    public NoticeDetailDto update(NoticeRequestDto request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权限操作");
        }
        
        Notice notice = noticeMapper.selectById(request.getId());
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setType(request.getType());
        notice.setPriority(request.getPriority());
        notice.setIsTop(request.getIsTop());

        noticeMapper.updateById(notice);
        log.info("管理员更新公告: {}", notice.getTitle());
        
        return convertToDetailDto(notice, false);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权限操作");
        }
        
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        
        noticeMapper.deleteById(id);
        log.info("管理员删除公告: {}", notice.getTitle());
    }

    @Override
    @Transactional
    public void publish(Long id) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权限操作");
        }

        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }

        // 已发布的不能重复发布
        if (notice.getStatus() == 1) {
            throw new BusinessException(400, "公告已发布");
        }

        // 设置发布时间为当前时间
        notice.setStatus(1);
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);

        log.info("管理员发布公告: {}", notice.getTitle());
    }

    @Override
    @Transactional
    public void unpublish(Long id) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权限操作");
        }
        
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        
        notice.setStatus(2);
        noticeMapper.updateById(notice);
        log.info("管理员下架公告: {}", notice.getTitle());
    }

    @Override
    public IPage<NoticeListDto> listForAdmin(Integer pageNum, Integer pageSize, 
                                               Integer status, String keyword) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "无权限查看");
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
        Long userId = UserContext.getUserId();
        
        Page<Notice> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notice::getStatus, 1)  // 已发布
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
        return resultPage;
    }

    @Override
    @Transactional
    public NoticeDetailDto getByIdForUser(Long id) {
        Long userId = UserContext.getUserId();
        
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        
        // 检查是否已发布
        if (notice.getStatus() != 1) {
            throw new BusinessException(404, "公告不存在");
        }
        
        // 检查是否已过期
        if (notice.getExpireTime() != null && notice.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(404, "公告已过期");
        }
        
        // 标记为已读
        markAsRead(id, userId);
        
        boolean isRead = noticeReadRecordMapper.exists(id, userId);
        
        return convertToDetailDto(notice, isRead);
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