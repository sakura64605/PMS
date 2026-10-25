package com.hongjie.pms.refactor.ai;
import com.hongjie.pms.refactor.ai.api.HumanTransferService;
import com.hongjie.pms.AI.modules.mapper.*;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
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
