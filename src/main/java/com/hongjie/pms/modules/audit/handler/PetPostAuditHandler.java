package com.hongjie.pms.modules.audit.handler;

import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.common.enums.AuditStatus;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.enums.TargetType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.search.event.PetPostUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PetPostAuditHandler implements AuditTargetHandler {

    private final PetPostMapper petPostMapper;
    private final MessageService messageService;
    private final DistributedCache distributedCache;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<String> getTargetTypes() {
        return List.of(TargetType.ADOPT.getCode(), TargetType.HELP.getCode());
    }

    @Override
    public Object getDetail(Long id) {
        PetPost pet = petPostMapper.selectById(id);
        if (pet == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "宠物信息不存在");
        }
        return pet;
    }

    @Override
    public void updateAuditStatus(Long targetId, Integer auditStatus, String rejectReason) {
        PetPost pet = petPostMapper.selectById(targetId);
        if (pet != null) {
            pet.setAuditStatus(auditStatus);
            pet.setStatus(AuditStatus.APPROVED.getCode().equals(auditStatus) ? 1 : 0);
            if (AuditStatus.APPROVED.getCode().equals(auditStatus)) {
                messageService.sendAuditPassNotification(
                        pet.getUserId(), pet.getTitle(), pet.getId(), "pet_post");
            } else if (AuditStatus.REJECTED.getCode().equals(auditStatus)) {
                messageService.sendAuditRejectNotification(
                        pet.getUserId(), pet.getTitle(), pet.getId(), "pet_post", rejectReason);
            }
            petPostMapper.updateById(pet);
            eventPublisher.publishEvent(new PetPostUpdatedEvent(this, targetId, "audit"));
        }
    }

    @Override
    public void clearCache(Long targetId) {
        String petCacheKey = CacheUtil.buildKey("pet", String.valueOf(targetId));
        distributedCache.delete(petCacheKey);
        String petListCacheKey = CacheUtil.buildKey("petList", "1", "10");
        distributedCache.delete(petListCacheKey);
    }

    @Override
    public String getTargetTypeDesc(String targetType) {
        if (TargetType.ADOPT.getCode().equals(targetType)) return "领养";
        if (TargetType.HELP.getCode().equals(targetType)) return "救助";
        return targetType;
    }

    @Override
    public String getTitle(Long targetId) {
        PetPost pet = petPostMapper.selectById(targetId);
        return pet != null ? pet.getTitle() : null;
    }

    @Override
    public Long getUserId(Long targetId) {
        PetPost pet = petPostMapper.selectById(targetId);
        return pet != null ? pet.getUserId() : null;
    }
}