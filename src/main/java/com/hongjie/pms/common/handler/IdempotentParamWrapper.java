package com.hongjie.pms.common.handler;

import com.hongjie.pms.common.annotation.Idempotent;
import com.hongjie.pms.common.enums.IdempotentTypeEnum;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.aspectj.lang.ProceedingJoinPoint;

@Data
@Builder
@Accessors(chain = true)
public final class IdempotentParamWrapper {

    /**
     * 幂等注解
     */
    private Idempotent idempotent;

    /**
     * AOP 处理连接点
     */
    private ProceedingJoinPoint joinPoint;

    /**
     * 锁标识，{@link IdempotentTypeEnum#PARAM}
     */
    private String lockKey;
}