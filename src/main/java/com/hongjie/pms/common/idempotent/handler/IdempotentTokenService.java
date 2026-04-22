package com.hongjie.pms.common.idempotent.handler;

import org.springframework.stereotype.Component;

public interface IdempotentTokenService extends IdempotentExecuteHandler {

    /**
     * 创建幂等验证Token
     */
    String createToken();
}
