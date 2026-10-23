package com.hongjie.pms.refactor.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.report.dto.*;
import com.hongjie.pms.modules.report.entity.ReportRecord;
import com.hongjie.pms.modules.report.enums.ReportStatus;
import com.hongjie.pms.modules.report.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.report.service.ReportService;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.core.page.PageQuery;
import com.hongjie.pms.refactor.core.strategy.StrategyRegistry;
import com.hongjie.pms.refactor.extension.target.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
public class PmsReportServiceImpl implements ReportService {
    private final ReportRecordMapper records;
    private final UserMapper users;
    private final ReportWriteMapper locks;
    private final ApplicationEventPublisher events;
    private final TargetTypeCodec codec;
    private final StrategyRegistry<BizTargetType,ReportTarget> targets;
    public PmsReportServiceImpl(ReportRecordMapper records,UserMapper users,ReportWriteMapper locks,
            ApplicationEventPublisher events,TargetTypeCodec codec,List<ReportTarget> targets){
        this.records=records;this.users=users;this.locks=locks;this.events=events;this.codec=codec;
        this.targets=new StrategyRegistry<>("举报目标",targets,t->List.of(t.type()));
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public void submit(Long userId,ReportRequest request){
        if(UserContext.getUserId()==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if(!Objects.equals(userId,UserContext.getUserId()))throw new BusinessException(ErrorCode.FORBIDDEN);
        if(request==null)throw new BusinessException(ErrorCode.PARAM_ERROR);
        validId(request.getTargetId());text(request.getReason(),"举报原因");
        var type=codec.read(request.getTargetType());var target=targets.get(type);
        if(!target.lock(request.getTargetId()))throw new BusinessException(ErrorCode.NOT_FOUND,"举报目标不存在");
        if(!locks.pending(userId,request.getTargetId(),type.getAliases()).isEmpty())
            throw new BusinessException(ErrorCode.PARAM_ERROR,"您已举报过该内容，请等待处理");
        var record=new ReportRecord();record.setReporterId(userId);record.setTargetId(request.getTargetId());
        record.setTargetType(codec.write(type,LegacyDialect.REPORT));record.setReason(request.getReason());
        record.setStatus(ReportStatus.PENDING.getCode());records.insert(record);
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public void handle(Long id,Long adminId,Integer status,String result){
        admin();validId(id);
        if(!Objects.equals(adminId,UserContext.getUserId()))throw new BusinessException(ErrorCode.FORBIDDEN);
        if(!Objects.equals(status,ReportStatus.HANDLED.getCode())&&!Objects.equals(status,ReportStatus.REJECTED.getCode()))
            throw new BusinessException(ErrorCode.PARAM_ERROR,"处理状态只能为已处理或已驳回");
        text(result,"处理结果");
        var record=load(id);
        if(!Objects.equals(record.getStatus(),ReportStatus.PENDING.getCode()))throw new BusinessException(ErrorCode.PARAM_ERROR,"该举报已处理");
        ReportTarget target=null;boolean exists=false;
        if(Objects.equals(status,ReportStatus.HANDLED.getCode())){
            target=targets.get(codec.read(record.getTargetType()));
            // 与 submit 保持目标 -> 举报记录的锁顺序，避免互相等待。
            exists=target.lock(record.getTargetId());
        }
        int changed=records.update(null,new LambdaUpdateWrapper<ReportRecord>().eq(ReportRecord::getId,id)
                .eq(ReportRecord::getStatus,ReportStatus.PENDING.getCode()).set(ReportRecord::getStatus,status)
                .set(ReportRecord::getHandlerId,adminId).set(ReportRecord::getHandleResult,result)
                .set(ReportRecord::getHandleTime,LocalDateTime.now()));
        if(changed!=1)throw new BusinessException(ErrorCode.PARAM_ERROR,"该举报已被其他管理员处理");
        if(target!=null&&exists){target.accept(record.getTargetId());events.publishEvent(new ReportTargetChanged(target.type(),record.getTargetId()));}
    }
    @Override public IPage<ReportListDto> getList(Integer status,String type,Integer number,Integer size){
        admin();if(status!=null&&Arrays.stream(ReportStatus.values()).noneMatch(s->s.getCode().equals(status)))throw new BusinessException(ErrorCode.PARAM_ERROR);
        var query=new LambdaQueryWrapper<ReportRecord>();if(status!=null)query.eq(ReportRecord::getStatus,status);
        if(StringUtils.hasText(type)){var resolved=codec.read(type);targets.get(resolved);query.in(ReportRecord::getTargetType,resolved.getAliases());}
        query.orderByDesc(ReportRecord::getCreateTime).orderByDesc(ReportRecord::getId);
        var normalized=PageQuery.of(number==null?null:number.longValue(),size==null?null:size.longValue());
        IPage<ReportRecord> page=records.selectPage(normalized.toPage(),query);
        Page<ReportListDto> result=new Page<>(page.getCurrent(),page.getSize(),page.getTotal());result.setRecords(convert(page.getRecords()));return result;
    }
    @Override public ReportListDto getDetail(Long id){admin();validId(id);return convert(List.of(load(id))).get(0);}
    private ReportRecord load(Long id){var row=records.selectById(id);if(row==null)throw new BusinessException(ErrorCode.NOT_FOUND,"举报记录不存在");return row;}
    private List<ReportListDto> convert(List<ReportRecord> rows){
        if(rows.isEmpty())return List.of();
        Set<Long> ids=new HashSet<>();for(var row:rows){if(row.getReporterId()!=null)ids.add(row.getReporterId());if(row.getHandlerId()!=null)ids.add(row.getHandlerId());}
        Map<Long,User> people=ids.isEmpty()?Map.of():users.selectBatchIds(ids).stream().collect(Collectors.toMap(User::getId,Function.identity()));
        Map<BizTargetType,Set<Long>> grouped=new HashMap<>();
        for(var row:rows)codec.tryRead(row.getTargetType()).filter(targets::contains).ifPresent(t->grouped.computeIfAbsent(t,k->new HashSet<>()).add(row.getTargetId()));
        Map<BizTargetType,Map<Long,String>> titles=new HashMap<>();grouped.forEach((t,keys)->titles.put(t,targets.get(t).titles(keys)));
        return rows.stream().map(row->{
            BizTargetType type=codec.tryRead(row.getTargetType()).filter(targets::contains).orElse(null);
            User reporter=row.getReporterId()==null?null:people.get(row.getReporterId());User handler=row.getHandlerId()==null?null:people.get(row.getHandlerId());
            String state=Arrays.stream(ReportStatus.values()).filter(s->s.getCode().equals(row.getStatus())).map(ReportStatus::getDesc).findFirst().orElse("未知");
            return ReportListDto.builder().id(row.getId()).targetId(row.getTargetId()).targetType(row.getTargetType())
                    .targetTypeDesc(type==null?row.getTargetType():targets.get(type).description())
                    .targetTitle(type==null?"未知":titles.get(type).getOrDefault(row.getTargetId(),"已删除"))
                    .reporterName(reporter==null?"未知":reporter.getUserName()).handlerName(handler==null?"":handler.getUserName())
                    .reason(row.getReason()).status(row.getStatus()).statusDesc(state).handleResult(row.getHandleResult())
                    .createTime(row.getCreateTime()).handleTime(row.getHandleTime()).build();
        }).toList();
    }
    private void admin(){if(!Integer.valueOf(1).equals(UserContext.getRole()))throw new BusinessException(ErrorCode.FORBIDDEN);}
    private void validId(Long id){if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);}
    private void text(String text,String name){if(!StringUtils.hasText(text)||text.length()>200)throw new BusinessException(ErrorCode.PARAM_ERROR,name+"需为1到200个字符");}
}
