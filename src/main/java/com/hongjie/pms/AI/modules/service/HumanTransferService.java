package com.hongjie.pms.AI.modules.service;

import com.hongjie.pms.AI.modules.entity.AiChatSession;
import com.hongjie.pms.AI.modules.entity.AiHumanTransfer;
import com.hongjie.pms.AI.modules.mapper.AiChatSessionMapper;
import com.hongjie.pms.AI.modules.mapper.AiHumanTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class HumanTransferService {
    
    private final AiHumanTransferMapper transferMapper;
    private final AiChatSessionMapper sessionMapper;
    
    public void requestTransfer(String sessionId, Long userId, String reason) {
        AiChatSession session = sessionMapper.selectBySessionId(sessionId);
        if (session != null) {
            session.setStatus(3);
            sessionMapper.updateById(session);
        }
        
        AiHumanTransfer transfer = new AiHumanTransfer();
        transfer.setSessionId(sessionId);
        transfer.setUserId(userId);
        transfer.setReason(reason);
        transfer.setStatus(1);
        transfer.setTransferredAt(LocalDateTime.now());
        transferMapper.insert(transfer);
        
        log.info("用户请求转人工: sessionId={}, userId={}", sessionId, userId);
    }
    
    public void acceptTransfer(Long transferId, Long adminId) {
        AiHumanTransfer transfer = transferMapper.selectById(transferId);
        if (transfer == null || transfer.getStatus() != 1) {
            throw new RuntimeException("转接记录不存在或已被处理");
        }
        
        transfer.setAdminId(adminId);
        transfer.setStatus(2);
        transferMapper.updateById(transfer);
    }
    
    public void closeTransfer(Long transferId) {
        AiHumanTransfer transfer = transferMapper.selectById(transferId);
        if (transfer != null) {
            transfer.setStatus(3);
            transfer.setClosedAt(LocalDateTime.now());
            transferMapper.updateById(transfer);
            
            AiChatSession session = sessionMapper.selectBySessionId(transfer.getSessionId());
            if (session != null) {
                session.setStatus(1);
                sessionMapper.updateById(session);
            }
        }
    }
}