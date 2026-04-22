package com.hongjie.pms.common.idempotent.handler;

import com.hongjie.pms.common.exception.ClientException;
import com.hongjie.pms.common.idempotent.annotation.Idempotent;
import com.hongjie.pms.common.idempotent.aspect.IdempotentAspect;
import com.hongjie.pms.common.utils.SpELUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@Slf4j
@RequiredArgsConstructor
public final class IdempotentSpELByRestAPIExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentSpELService {

    private final RedissonClient redissonClient;

    private final static String LOCK = "lock:spEL:restAPI";

    @SneakyThrows
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        Idempotent idempotent = IdempotentAspect.getIdempotent(joinPoint);
        String key = (String) SpELUtil.parseKey(idempotent.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
        log.info("SpEL 表达式: {}, 解析结果: {}", idempotent.key(), key);
        return IdempotentParamWrapper.builder().lockKey(key).joinPoint(joinPoint).build();
    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        String uniqueKey = wrapper.getIdempotent().uniqueKeyPrefix() + wrapper.getLockKey();
        log.info("尝试获取分布式锁, key: {}", uniqueKey);
        RLock lock = redissonClient.getLock(uniqueKey);
        boolean lockAcquired = lock.tryLock();

        log.info("锁获取结果: {}", lockAcquired);
        if (!lock.tryLock()) {
            log.warn("获取锁失败，重复提交被拦截, key: {}", uniqueKey);
            throw new ClientException(wrapper.getIdempotent().message());
        }

        log.info("成功获取锁, key: {}", uniqueKey);
        IdempotentContext.put(LOCK, lock);
    }

    @Override
    public void postProcessing() {
        RLock lock = null;
        try {
            lock = (RLock) IdempotentContext.getKey(LOCK);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    @Override
    public void exceptionProcessing() {
        RLock lock = null;
        try {
            lock = (RLock) IdempotentContext.getKey(LOCK);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }
}
