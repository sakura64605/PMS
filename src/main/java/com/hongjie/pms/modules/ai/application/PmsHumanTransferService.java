package com.hongjie.pms.modules.ai.application;
import com.hongjie.pms.modules.ai.api.service.HumanTransferService;
import com.hongjie.pms.modules.ai.infrastructure.persistence.mapper.*;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
@Service @Primary
public class PmsHumanTransferService implements HumanTransferService {
    private final AiTransfers transfers;
    public PmsHumanTransferService(AiTransfers transfers){
        this.transfers=transfers;
    }
    @Override public void requestTransfer(String id,Long actor,String reason){
        if(actor==null||!actor.equals(UserContext.getUserId()))throw new BusinessException(ErrorCode.UNAUTHORIZED);
        transfers.request(id,actor,reason);
    }
    @Override public void acceptTransfer(Long id,Long admin){transfers.accept(id,admin);}
    @Override public void closeTransfer(Long id){transfers.close(id);}
}
