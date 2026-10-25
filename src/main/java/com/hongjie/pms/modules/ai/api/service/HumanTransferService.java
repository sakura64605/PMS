package com.hongjie.pms.modules.ai.api.service;
/** Runtime contract implemented by the migrated service. */
public interface HumanTransferService {
    void requestTransfer(String sessionId, Long userId, String reason);
    void acceptTransfer(Long transferId, Long adminId);
    void closeTransfer(Long transferId);
}
