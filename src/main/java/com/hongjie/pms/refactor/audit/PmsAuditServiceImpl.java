package com.hongjie.pms.refactor.audit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.audit.dto.*;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.audit.handler.AuditTargetHandlerFactory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.List;

/** 审核门面：查询和命令分别委托新服务；详情暂保留原 DTO 读取方式。 */
@Service
@Primary
public class PmsAuditServiceImpl implements AuditService {
    private final AuditTargetHandlerFactory details;
    private final AuditQueryService queries;
    private final AuditCommandService commands;
    public PmsAuditServiceImpl(AuditTargetHandlerFactory details, AuditQueryService queries, AuditCommandService commands) {
        this.details = details;
        this.queries = queries;
        this.commands = commands;
    }
    public void submit(String type, Long id) { commands.submit(type, id); }
    public void approve(Long id, String type) { commands.approve(id, type); }
    public void reject(Long id, String type, String reason) { commands.reject(id, type, reason); }
    public void batchApprove(List<Long> ids, String type) { commands.batchApprove(ids, type); }
    public void batchReject(List<Long> ids, String type, String reason) { commands.batchReject(ids, type, reason); }
    public Object getDetail(Long id, String type) { return details.getHandler(type).getDetail(id); }
    public IPage<AuditListDto> getPendingList(String type, String keyword, String range, Integer page, Integer size) {
        return queries.pending(type, keyword, range, page, size);
    }
    public IPage<AuditHistoryDto> getHistoryList(String type, String keyword, Integer status, Integer page, Integer size) {
        return queries.history(type, keyword, status, page, size);
    }
}
