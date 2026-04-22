package com.hongjie.pms.common.handler;

import com.hongjie.pms.common.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;

public abstract class AbstractIdempotentExecuteHandler implements IdempotentExecuteHandler{

    protected abstract IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint);

    public void execute(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        // 模板方法模式：构建幂等参数包装器
        IdempotentParamWrapper idempotentParamWrapper = buildWrapper(joinPoint).setIdempotent(idempotent);
        handler(idempotentParamWrapper);
    }

}
