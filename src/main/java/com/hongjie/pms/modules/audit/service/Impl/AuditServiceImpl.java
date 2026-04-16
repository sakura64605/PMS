package com.hongjie.pms.modules.audit.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRecordMapper auditRecordMapper;
    private final PetPostMapper petPostMapper;
    private final ActivityMapper activityMapper;
    private final MessageService messageService;

    /**
     * 提交审核（创建审核记录，设置 auditStatus = 0）
     */
    /**
     * 提交审核
     */
    @Transactional
    public void submit(String targetType, Long targetId) {
        // 检查是否已有待审核记录
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getTargetId, targetId)
                .eq(AuditRecord::getAuditStatus, 0);
        AuditRecord existing = auditRecordMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该内容已在审核中");
        }

        // 创建审核记录
        AuditRecord record = new AuditRecord();
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setAuditStatus(0);
        auditRecordMapper.insert(record);

        // 更新原内容的审核状态
        if ("pet".equals(targetType)) {
            PetPost pet = petPostMapper.selectById(targetId);
            pet.setAuditStatus(0);
            petPostMapper.updateById(pet);
        } else if ("activity".equals(targetType)) {
            Activity activity = activityMapper.selectById(targetId);
            activity.setAuditStatus(0);
            activityMapper.updateById(activity);
        }

        log.info("提交审核: targetType={}, targetId={}", targetType, targetId);
    }

    /**
     * 审核通过
     */
    @Transactional
    public void approve(String targetType, Long targetId) {
        AuditRecord record = getPendingRecord(targetType, targetId);

        record.setAuditStatus(1);
        record.setAuditorId(UserContext.getUserId());
        record.setAuditTime(LocalDateTime.now());
        auditRecordMapper.updateById(record);

        if ("pet".equals(targetType)) {
            PetPost pet = petPostMapper.selectById(targetId);
            pet.setAuditStatus(1);
            pet.setStatus(1);
            petPostMapper.updateById(pet);
            messageService.sendAuditPassNotification(
                    pet.getUserId(), pet.getTitle(), pet.getId(), "pet_post"
            );
        } else if ("activity".equals(targetType)) {
            Activity activity = activityMapper.selectById(targetId);
            activity.setAuditStatus(1);
            activity.setStatus(1);
            activityMapper.updateById(activity);
            messageService.sendAuditPassNotification(
                    activity.getUserId(), activity.getTitle(), activity.getId(), "activity"
            );
        }

        log.info("审核通过: targetType={}, targetId={}", targetType, targetId);
    }

    /**
     * 审核拒绝
     */
    @Transactional
    public void reject(String targetType, Long targetId, String reason) {
        AuditRecord record = getPendingRecord(targetType, targetId);

        record.setAuditStatus(2);
        record.setRejectReason(reason);
        record.setAuditorId(UserContext.getUserId());
        record.setAuditTime(LocalDateTime.now());
        auditRecordMapper.updateById(record);

        if ("pet".equals(targetType)) {
            PetPost pet = petPostMapper.selectById(targetId);
            pet.setAuditStatus(2);
            petPostMapper.updateById(pet);
            messageService.sendAuditRejectNotification(
                    pet.getUserId(), pet.getTitle(), pet.getId(), "pet_post", reason
            );
        } else if ("activity".equals(targetType)) {
            Activity activity = activityMapper.selectById(targetId);
            activity.setAuditStatus(2);
            activityMapper.updateById(activity);
            messageService.sendAuditRejectNotification(
                    activity.getUserId(), activity.getTitle(), activity.getId(), "activity", reason
            );
        }

        log.info("审核拒绝: targetType={}, targetId={}, reason={}", targetType, targetId, reason);
    }

    /**
     * 待审核列表（分页）
     */
    public IPage<AuditRecord> getPendingList(String targetType, Integer pageNum, Integer pageSize) {
        Page<AuditRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getAuditStatus, 0)
                .orderByDesc(AuditRecord::getCreateTime);
        return auditRecordMapper.selectPage(page, wrapper);
    }

    /**
     * 审核历史列表（分页）
     */
    public IPage<AuditRecord> getHistoryList(String targetType, Long targetId, Integer pageNum, Integer pageSize) {
        Page<AuditRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getTargetId, targetId)
                .orderByDesc(AuditRecord::getCreateTime);
        return auditRecordMapper.selectPage(page, wrapper);
    }

    private AuditRecord getPendingRecord(String targetType, Long targetId) {
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getTargetId, targetId)
                .eq(AuditRecord::getAuditStatus, 0);
        AuditRecord record = auditRecordMapper.selectOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "待审核记录不存在");
        }
        return record;
    }
}