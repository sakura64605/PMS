package com.hongjie.pms.modules.audit.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.AuditStatus;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.enums.TargetType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.audit.dto.AuditHistoryDto;
import com.hongjie.pms.modules.audit.dto.AuditListDto;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRecordMapper auditRecordMapper;
    private final PetPostMapper petPostMapper;
    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final DistributedCache distributedCache;
    private final DailyPostMapper dailyPostMapper;

    // ==================== 提交审核 ====================

    @Override
    @Transactional
    public void submit(String targetType, Long targetId) {
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getTargetId, targetId)
                .eq(AuditRecord::getAuditStatus, AuditStatus.PENDING.getCode());
        AuditRecord existing = auditRecordMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该内容已在审核中");
        }

        AuditRecord record = new AuditRecord();
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setAuditStatus(AuditStatus.PENDING.getCode());
        auditRecordMapper.insert(record);

        clearCache(targetType, targetId);

        log.info("提交审核: targetType={}, targetId={}", targetType, targetId);
    }

    // ==================== 单个审核 ====================

    @Override
    @Transactional
    public void approve(Long id, String targetType) {
        AuditRecord record = getPendingRecord(targetType, id);
        record.setAuditorId(UserContext.getUserId());
        record.setAuditTime(LocalDateTime.now());
        record.setAuditStatus(AuditStatus.APPROVED.getCode());
        auditRecordMapper.updateById(record);

        if (TargetType.HELP.name().equals(targetType) || TargetType.ADOPT.name().equals(targetType)) {
            PetPost pet = petPostMapper.selectById(id);
            pet.setAuditStatus(AuditStatus.APPROVED.getCode());
            petPostMapper.updateById(pet);
        } else if (TargetType.ACTIVITY.name().equals(targetType)) {
            Activity activity = activityMapper.selectById(id);
            activity.setAuditStatus(AuditStatus.APPROVED.getCode());
        } else if (TargetType.DAILY.name().equals(targetType)) {
            DailyPost daily = dailyPostMapper.selectById(id);
            daily.setAuditStatus(AuditStatus.APPROVED.getCode());
        }

        clearCache(targetType, id);

        log.info("审核通过: targetType={}, targetId={}", targetType, id);
    }

    @Override
    @Transactional
    public void reject(Long id, String targetType, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "拒绝理由不能为空");
        }

        AuditRecord record = getPendingRecord(targetType, id);
        record.setAuditStatus(AuditStatus.REJECTED.getCode());
        record.setRejectReason(reason);
        record.setAuditorId(UserContext.getUserId());
        record.setAuditTime(LocalDateTime.now());
        auditRecordMapper.updateById(record);

        updateTargetAuditStatus(targetType, id, AuditStatus.REJECTED.getCode(), reason);
        clearCache(targetType, id);

        log.info("审核拒绝: targetType={}, targetId={}, reason={}", targetType, id, reason);
    }

    // ==================== 批量审核 ====================

    @Override
    @Transactional
    public void batchApprove(List<Long> ids, String targetType) {
        for (Long id : ids) {
            try {
                approve(id, targetType);
            } catch (Exception e) {
                log.error("批量审核通过失败: id={}, error={}", id, e.getMessage());
                throw new BusinessException(ErrorCode.PARAM_ERROR, "批量审核失败：" + e.getMessage());
            }
        }
        log.info("批量审核通过: targetType={}, ids={}", targetType, ids);
    }

    @Override
    @Transactional
    public void batchReject(List<Long> ids, String targetType, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "拒绝理由不能为空");
        }
        for (Long id : ids) {
            try {
                reject(id, targetType, reason);
            } catch (Exception e) {
                log.error("批量审核拒绝失败: id={}, error={}", id, e.getMessage());
                throw new BusinessException(ErrorCode.PARAM_ERROR, "批量审核失败：" + e.getMessage());
            }
        }
        log.info("批量审核拒绝: targetType={}, ids={}, reason={}", targetType, ids, reason);
    }

    // ==================== 待审核列表 ====================

    @Override
    public IPage<AuditListDto> getPendingList(String targetType, String keyword,
                                              String dateRange, Integer pageNum, Integer pageSize) {
        boolean queryAll = !StringUtils.hasText(targetType);
        List<AuditListDto> resultList = new ArrayList<>();

        // 查询领养
        if (queryAll || TargetType.ADOPT.getCode().equals(targetType)) {
            LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<PetPost>()
                    .eq(PetPost::getType, 0)
                    .eq(PetPost::getAuditStatus, AuditStatus.PENDING.getCode());
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w.like(PetPost::getTitle, keyword).or().like(PetPost::getContent, keyword));
            }
            wrapper.orderByDesc(PetPost::getCreateTime);
            List<PetPost> list = petPostMapper.selectList(wrapper);
            for (PetPost item : list) {
                resultList.add(convertPetToDto(item, TargetType.ADOPT.getCode()));
            }
        }

        // 查询救助
        if (queryAll || TargetType.HELP.getCode().equals(targetType)) {
            LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<PetPost>()
                    .eq(PetPost::getType, 1)
                    .eq(PetPost::getAuditStatus, AuditStatus.PENDING.getCode());
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w.like(PetPost::getTitle, keyword).or().like(PetPost::getContent, keyword));
            }
            wrapper.orderByDesc(PetPost::getCreateTime);
            List<PetPost> list = petPostMapper.selectList(wrapper);
            for (PetPost item : list) {
                resultList.add(convertPetToDto(item, TargetType.HELP.getCode()));
            }
        }

        // 查询活动
        if (queryAll || TargetType.ACTIVITY.getCode().equals(targetType)) {
            LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<Activity>()
                    .eq(Activity::getAuditStatus, AuditStatus.PENDING.getCode());
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w.like(Activity::getTitle, keyword).or().like(Activity::getContent, keyword));
            }
            wrapper.orderByDesc(Activity::getCreateTime);
            List<Activity> list = activityMapper.selectList(wrapper);
            for (Activity item : list) {
                resultList.add(convertActivityToDto(item));
            }
        }

        if (queryAll || TargetType.DAILY.getCode().equals(targetType)) {
            LambdaQueryWrapper<DailyPost> wrapper = new LambdaQueryWrapper<DailyPost>()
                    .eq(DailyPost::getAuditStatus, AuditStatus.PENDING.getCode())
                    .eq(DailyPost::getStatus, 1);

            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w
                        .like(DailyPost::getContent, keyword)
                        .or()
                        .like(DailyPost::getLocation, keyword)
                );
            }
            wrapper.orderByDesc(DailyPost::getCreateTime);
            List<DailyPost> list = dailyPostMapper.selectList(wrapper);
            for (DailyPost item : list) {
                resultList.add(convertDailyToDto(item));
            }
        }

        // 按时间筛选
        if (StringUtils.hasText(dateRange)) {
            LocalDateTime startTime = parseDateRange(dateRange);
            if (startTime != null) {
                resultList = resultList.stream()
                        .filter(dto -> dto.getCreateTime().isAfter(startTime))
                        .collect(Collectors.toList());
            }
        }

        // 按时间倒序
        resultList.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        // 分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, resultList.size());
        Page<AuditListDto> page = new Page<>(pageNum, pageSize, resultList.size());
        page.setRecords(resultList.subList(start, end));
        return page;
    }

    // ==================== 审核历史 ====================

    @Override
    public IPage<AuditHistoryDto> getHistoryList(String targetType, String keyword,
                                                 Integer auditStatus, Integer pageNum, Integer pageSize) {
        Page<AuditRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<AuditRecord>();

        if (StringUtils.hasText(targetType)) {
            wrapper.eq(AuditRecord::getTargetType, targetType);
        }
        if (auditStatus != null) {
            wrapper.eq(AuditRecord::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(AuditRecord::getCreateTime);

        IPage<AuditRecord> recordPage = auditRecordMapper.selectPage(page, wrapper);

        List<AuditHistoryDto> records = new ArrayList<>();
        for (AuditRecord record : recordPage.getRecords()) {
            AuditHistoryDto dto = convertRecordToHistoryDto(record, keyword);
            if (dto != null) {
                records.add(dto);
            }
        }

        Page<AuditHistoryDto> resultPage = new Page<>(pageNum, pageSize, recordPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    // ==================== 详情 ====================

    @Override
    public Object getDetail(Long id, String targetType) {
        if (TargetType.ADOPT.getCode().equals(targetType) || TargetType.HELP.getCode().equals(targetType)) {
            PetPost pet = petPostMapper.selectById(id);
            if (pet == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "宠物信息不存在");
            }
            return pet;
        } else if (TargetType.ACTIVITY.getCode().equals(targetType)) {
            Activity activity = activityMapper.selectById(id);
            if (activity == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
            }
            return activity;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的类型");
    }

    // ==================== 私有方法 ====================

    private AuditRecord getPendingRecord(String targetType, Long targetId) {
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getTargetId, targetId)
                .eq(AuditRecord::getAuditStatus, AuditStatus.PENDING.getCode());
        AuditRecord record = auditRecordMapper.selectOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "待审核记录不存在");
        }
        return record;
    }

    private AuditListDto convertDailyToDto(DailyPost daily) {
        User user = userMapper.selectById(daily.getUserId());
        UserSimpleDto userDto = user != null ? UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build() : null;

        return AuditListDto.builder()
                .id(daily.getId())
                .targetType(TargetType.DAILY.getCode())
                .targetTypeDesc("日常动态")
                .title(daily.getContent() != null && daily.getContent().length() > 50
                        ? daily.getContent().substring(0, 50) + "..."
                        : daily.getContent())
                .content(daily.getContent())
                .images(daily.getImages())
                .user(userDto)
                .createTime(daily.getCreateTime())
                .auditStatus(daily.getAuditStatus())
                .auditStatusDesc(AuditStatus.getDescByCode(daily.getAuditStatus()))
                .location(daily.getLocation())
                .build();
    }

    private void updateTargetAuditStatus(String targetType, Long targetId, Integer auditStatus, String rejectReason) {
        if (TargetType.ADOPT.getCode().equals(targetType) || TargetType.HELP.getCode().equals(targetType)) {
            PetPost pet = petPostMapper.selectById(targetId);
            if (pet != null) {
                pet.setAuditStatus(auditStatus);
                if (AuditStatus.APPROVED.getCode().equals(auditStatus)) {
                    pet.setStatus(1);
                    messageService.sendAuditPassNotification(
                            pet.getUserId(), pet.getTitle(), pet.getId(), "pet_post");
                } else if (AuditStatus.REJECTED.getCode().equals(auditStatus)) {
                    messageService.sendAuditRejectNotification(
                            pet.getUserId(), pet.getTitle(), pet.getId(), "pet_post", rejectReason);
                }
                petPostMapper.updateById(pet);
            }
        } else if (TargetType.ACTIVITY.getCode().equals(targetType)) {
            Activity activity = activityMapper.selectById(targetId);
            if (activity != null) {
                activity.setAuditStatus(auditStatus);
                if (AuditStatus.APPROVED.getCode().equals(auditStatus)) {
                    activity.setStatus(1);
                    messageService.sendAuditPassNotification(
                            activity.getUserId(), activity.getTitle(), activity.getId(), "activity");
                } else if (AuditStatus.REJECTED.getCode().equals(auditStatus)) {
                    messageService.sendAuditRejectNotification(
                            activity.getUserId(), activity.getTitle(), activity.getId(), "activity", rejectReason);
                }
                activityMapper.updateById(activity);
            }
        }
    }

    private void clearCache(String targetType, Long targetId) {
        if (TargetType.ADOPT.getCode().equals(targetType) || TargetType.HELP.getCode().equals(targetType)) {
            // 清除宠物详情缓存
            String petCacheKey = CacheUtil.buildKey("pet", String.valueOf(targetId));
            distributedCache.delete(petCacheKey);
            
            // 清除宠物列表缓存
            String petListCacheKey = CacheUtil.buildKey("petList", "1", "10");
            distributedCache.delete(petListCacheKey);
        } else if (TargetType.ACTIVITY.getCode().equals(targetType)) {
            // 清除活动详情缓存
            String activityCacheKey = CacheUtil.buildKey("activity", String.valueOf(targetId));
            distributedCache.delete(activityCacheKey);
            
            // 清除活动列表缓存
            String activityListCacheKey = CacheUtil.buildKey("activityList", "1", "10");
            distributedCache.delete(activityListCacheKey);
        }
    }

    private AuditListDto convertPetToDto(PetPost pet, String targetType) {
        User user = userMapper.selectById(pet.getUserId());
        UserSimpleDto userDto = user != null ? UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build() : null;

        return AuditListDto.builder()
                .id(pet.getId())
                .targetType(targetType)
                .targetTypeDesc(TargetType.ADOPT.getCode().equals(targetType) ? "领养" : "救助")
                .title(pet.getTitle())
                .content(pet.getContent().length() > 100 ? pet.getContent().substring(0, 100) + "..." : pet.getContent())
                .images(pet.getImages())
                .user(userDto)
                .createTime(pet.getCreateTime())
                .auditStatus(pet.getAuditStatus())
                .auditStatusDesc(AuditStatus.getDescByCode(pet.getAuditStatus()))
                .petType(pet.getPetType())
                .petName(pet.getPetName())
                .petAge(pet.getPetAge())
                .petGender(pet.getPetGender())
                .address(pet.getAddress())
                .contactPhone(pet.getContactPhone())
                .build();
    }

    private AuditListDto convertActivityToDto(Activity activity) {
        User user = userMapper.selectById(activity.getUserId());
        UserSimpleDto userDto = user != null ? UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build() : null;

        return AuditListDto.builder()
                .id(activity.getId())
                .targetType(TargetType.ACTIVITY.getCode())
                .targetTypeDesc("活动")
                .title(activity.getTitle())
                .content(activity.getContent().length() > 100 ? activity.getContent().substring(0, 100) + "..." : activity.getContent())
                .images(activity.getImages())
                .user(userDto)
                .createTime(activity.getCreateTime())
                .auditStatus(activity.getAuditStatus())
                .auditStatusDesc(AuditStatus.getDescByCode(activity.getAuditStatus()))
                .location(activity.getLocation())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .maxPeople(activity.getMaxPeople())
                .currentPeople(activity.getCurrentPeople())
                .build();
    }

    private AuditHistoryDto convertRecordToHistoryDto(AuditRecord record, String keyword) {
        String title = null;
        Long userId = null;

        if (TargetType.ADOPT.getCode().equals(record.getTargetType()) ||
                TargetType.HELP.getCode().equals(record.getTargetType())) {
            PetPost pet = petPostMapper.selectById(record.getTargetId());
            if (pet != null) {
                title = pet.getTitle();
                userId = pet.getUserId();
            }
        } else if (TargetType.ACTIVITY.getCode().equals(record.getTargetType())) {
            Activity activity = activityMapper.selectById(record.getTargetId());
            if (activity != null) {
                title = activity.getTitle();
                userId = activity.getUserId();
            }
        } else if (TargetType.DAILY.getCode().equals(record.getTargetType())) {
            DailyPost daily = dailyPostMapper.selectById(record.getTargetId());
            if (daily != null) {
                title = daily.getContent();
                userId = daily.getUserId();
            }
        }

        if (title == null) return null;
        if (StringUtils.hasText(keyword) && !title.contains(keyword)) return null;

        User user = userId != null ? userMapper.selectById(userId) : null;
        UserSimpleDto userDto = user != null ? UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build() : null;

        User auditor = record.getAuditorId() != null ? userMapper.selectById(record.getAuditorId()) : null;

        return AuditHistoryDto.builder()
                .id(record.getId())
                .targetType(record.getTargetType())
                .targetTypeDesc(getTargetTypeDesc(record.getTargetType()))
                .targetId(record.getTargetId())
                .title(title)
                .user(userDto)
                .auditStatus(record.getAuditStatus())
                .auditStatusDesc(AuditStatus.getDescByCode(record.getAuditStatus()))
                .rejectReason(record.getRejectReason())
                .auditorName(auditor != null ? auditor.getUserName() : null)
                .auditTime(record.getAuditTime())
                .createTime(record.getCreateTime())
                .build();
    }

    private String getTargetTypeDesc(String targetType) {
        if (TargetType.ADOPT.getCode().equals(targetType)) return "领养";
        if (TargetType.HELP.getCode().equals(targetType)) return "救助";
        if (TargetType.ACTIVITY.getCode().equals(targetType)) return "活动";
        return targetType;
    }

    private LocalDateTime parseDateRange(String dateRange) {
        if ("today".equals(dateRange)) {
            return LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        } else if ("week".equals(dateRange)) {
            return LocalDateTime.now().minusDays(7);
        } else if ("month".equals(dateRange)) {
            return LocalDateTime.now().minusMonths(1);
        }
        return null;
    }
}