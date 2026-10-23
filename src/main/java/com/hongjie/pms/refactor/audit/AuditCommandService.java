package com.hongjie.pms.refactor.audit;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.refactor.core.strategy.StrategyRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuditCommandService {
    private final StrategyRegistry<String,AuditWriteTarget> targets;
    private final AuditWriteMapper locks;
    private final AuditRecordMapper records;
    private final ApplicationEventPublisher events;
    public AuditCommandService(List<AuditWriteTarget> targets,AuditWriteMapper locks,AuditRecordMapper records,ApplicationEventPublisher events){
        this.targets=new StrategyRegistry<>("审核写入",targets,t->List.of(t.type()));
        this.locks=locks;this.records=records;this.events=events;
    }
    @Transactional(rollbackFor=Exception.class)
    public void submit(String type,Long id){
        validateId(id);
        var target=targets.get(type);target.lock(id);
        if(!locks.pending(type,id).isEmpty())throw new BusinessException(ErrorCode.PARAM_ERROR,"该内容已在审核中");
        var record=new AuditRecord();record.setTargetType(type);record.setTargetId(id);record.setAuditStatus(0);
        records.insert(record);
        events.publishEvent(new AuditChanged(type,id,null,null,null));
    }
    @Transactional(rollbackFor=Exception.class)
    public void approve(Long id,String type){ requireAdmin();decide(id,type,1,null); }
    @Transactional(rollbackFor=Exception.class)
    public void reject(Long id,String type,String reason){ requireAdmin();requireReason(reason);decide(id,type,2,reason); }
    @Transactional(rollbackFor=Exception.class)
    public void batchApprove(List<Long> ids,String type){batch(ids,type,1,null);}
    @Transactional(rollbackFor=Exception.class)
    public void batchReject(List<Long> ids,String type,String reason){requireReason(reason);batch(ids,type,2,reason);}
    private void batch(List<Long> ids,String type,int status,String reason){
        requireAdmin();
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id->id==null||id<=0))
            throw new BusinessException(ErrorCode.PARAM_ERROR,"批量审核需包含1到100个有效ID");
        // 同类型按 ID 固定锁顺序，减少两个批次反向加锁导致的死锁。
        for(Long id:new TreeSet<>(ids))decide(id,type,status,reason);
    }
    private void decide(Long id,String type,int status,String reason){
        validateId(id);var target=targets.get(type);target.lock(id);
        var pending=locks.pending(type,id);
        if(pending.size()!=1)throw new BusinessException(ErrorCode.PARAM_ERROR,
                pending.isEmpty()?"待审核记录不存在或已处理":"存在重复待审记录，请先核实历史数据");
        var snapshot=target.load(id);var record=pending.get(0);
        int changed=records.update(null,new LambdaUpdateWrapper<AuditRecord>()
                .eq(AuditRecord::getId,record.getId()).eq(AuditRecord::getAuditStatus,0)
                .set(AuditRecord::getAuditStatus,status).set(AuditRecord::getRejectReason,reason)
                .set(AuditRecord::getAuditorId,UserContext.getUserId()).set(AuditRecord::getAuditTime,LocalDateTime.now()));
        if(changed!=1)throw new BusinessException(ErrorCode.PARAM_ERROR,"审核记录已变化，请刷新后重试");
        target.decide(id,status);
        events.publishEvent(new AuditChanged(type,id,status,reason,snapshot));
    }
    private void validateId(Long id){if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);}
    private void requireReason(String reason){if(!StringUtils.hasText(reason))throw new BusinessException(ErrorCode.PARAM_ERROR,"请填写拒绝原因");}
    private void requireAdmin(){if(!UserContext.isAdmin())throw new BusinessException(ErrorCode.FORBIDDEN);}
}
