package com.hongjie.pms.common.circuitbreaker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器管理器
 */
@Slf4j
@Component
public class CircuitBreakerManager {
    
    private final ConcurrentHashMap<String, CircuitBreaker> breakerMap = new ConcurrentHashMap<>();
    
    /**
     * 获取或创建熔断器
     */
    public CircuitBreaker getOrCreate(String resourceName, CircuitBreakerConfig config) {
        return breakerMap.computeIfAbsent(resourceName, key -> {
            log.info("创建熔断器: {}", resourceName);
            return new CircuitBreaker(resourceName, config);
        });
    }
    
    /**
     * 获取熔断器
     */
    public CircuitBreaker get(String resourceName) {
        return breakerMap.get(resourceName);
    }
    
    /**
     * 移除熔断器
     */
    public void remove(String resourceName) {
        breakerMap.remove(resourceName);
    }
    
    /**
     * 获取所有熔断器状态
     */
    public void printAllStatus() {
        for (var entry : breakerMap.entrySet()) {
            CircuitBreaker breaker = entry.getValue();
            log.info("熔断器 [{}] 状态: {}, 统计: {}", 
                entry.getKey(), 
                breaker.getState(),
                breaker.getStatData());
        }
    }
}