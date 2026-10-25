package com.hongjie.pms.modules.ai.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.ai.infrastructure.persistence.mapper.*;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiTransfers {
    private final AiSessions access;
    private final AiChatSessionMapper sessions;
    private final AiHumanTransferMapper transfers;
    @Transactional public void request(String id,Long actor,String reason){
        if(reason!=null&&reason.length()>500)throw new BusinessException(ErrorCode.PARAM_ERROR);
        var session=access.require(id,actor,true,false);
        var open=transfers.selectList(new LambdaQueryWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getSessionId,id).in(AiHumanTransfer::getStatus,List.of(AiTransferState.REQUESTED.code,AiTransferState.ACCEPTED.code)));
        if(open.size()>1)throw new BusinessException(ErrorCode.PARAM_ERROR,"存在重复转接记录，请先核实");
        if(open.isEmpty()){
            var row=new AiHumanTransfer();row.setSessionId(id);row.setUserId(actor);row.setReason(reason);
            row.setStatus(AiTransferState.REQUESTED.code);row.setTransferredAt(LocalDateTime.now());
            if(transfers.insert(row)!=1)throw new BusinessException(ErrorCode.DB_ERROR);
        }else if(!actor.equals(open.get(0).getUserId()))throw new BusinessException(ErrorCode.PARAM_ERROR,"转接记录归属异常");
        status(session,AiSessionState.HUMAN.code);
    }
    @Transactional public void accept(Long id,Long admin){
        requireAdmin(admin);var pair=lock(id);var session=pair.session();var transfer=pair.transfer();
        if(!Objects.equals(session.getStatus(),AiSessionState.HUMAN.code)||!Objects.equals(transfer.getStatus(),AiTransferState.REQUESTED.code))
            throw new BusinessException(ErrorCode.PARAM_ERROR,"转接不存在或已处理");
        if(transfers.update(null,new LambdaUpdateWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getId,id).eq(AiHumanTransfer::getStatus,AiTransferState.REQUESTED.code)
                .set(AiHumanTransfer::getAdminId,admin).set(AiHumanTransfer::getStatus,AiTransferState.ACCEPTED.code))!=1)throw new BusinessException(ErrorCode.DB_ERROR);
    }
    @Transactional public void close(Long id){
        requireAdmin(UserContext.getUserId());var pair=lock(id);var transfer=pair.transfer();
        if(Objects.equals(transfer.getStatus(),AiTransferState.CLOSED.code))return;
        if(Objects.equals(transfer.getStatus(),AiTransferState.ACCEPTED.code)&&!Objects.equals(transfer.getAdminId(),UserContext.getUserId()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        if(transfers.update(null,new LambdaUpdateWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getId,id)
                .in(AiHumanTransfer::getStatus,List.of(AiTransferState.REQUESTED.code,AiTransferState.ACCEPTED.code)).set(AiHumanTransfer::getStatus,AiTransferState.CLOSED.code).set(AiHumanTransfer::getClosedAt,LocalDateTime.now()))!=1)
            throw new BusinessException(ErrorCode.DB_ERROR);
        if(Objects.equals(pair.session().getStatus(),AiSessionState.HUMAN.code))status(pair.session(),AiSessionState.ACTIVE.code);
    }
    private Locked lock(Long id){
        if(id==null||id<=0)throw new BusinessException(ErrorCode.PARAM_ERROR);
        var hint=transfers.selectById(id);if(hint==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        // All mutations lock session first, then transfer, including deletion.
        var session=sessions.selectOne(new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getSessionId,hint.getSessionId()).last("FOR UPDATE"));
        if(session==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        var transfer=transfers.selectOne(new LambdaQueryWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getId,id).last("FOR UPDATE"));
        if(transfer==null||!Objects.equals(transfer.getSessionId(),session.getSessionId())||!Objects.equals(transfer.getUserId(),session.getUserId()))
            throw new BusinessException(ErrorCode.NOT_FOUND);
        return new Locked(session,transfer);
    }
    private void status(AiChatSession row,int status){
        if(sessions.update(null,new LambdaUpdateWrapper<AiChatSession>().eq(AiChatSession::getId,row.getId())
                .set(AiChatSession::getStatus,status).set(AiChatSession::getUpdatedAt,LocalDateTime.now()))!=1)
            throw new BusinessException(ErrorCode.DB_ERROR);
    }
    private void requireAdmin(Long admin){
        if(!Integer.valueOf(1).equals(UserContext.getRole())||admin==null||!admin.equals(UserContext.getUserId()))throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private record Locked(AiChatSession session,AiHumanTransfer transfer){}
}
