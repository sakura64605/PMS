package com.hongjie.pms.common.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CircuitBreaker {

    private final String resourceName;
    private final CircuitBreakerConfig config;
    private final SlidingWindow slidingWindow;

    private volatile CircuitState state = CircuitState.CLOSED;
    private long openTime = 0;
    private int halfOpenSuccessCount = 0;
    private int halfOpenRequestCount = 0;
    private FallbackFunction fallbackFunction;

    public CircuitBreaker(String resourceName, CircuitBreakerConfig config) {
        this.resourceName = resourceName;
        this.config = config;
        this.slidingWindow = new SlidingWindow(config.getWindowSize(), config.getBucketCount());
    }

    public CircuitBreaker withFallback(FallbackFunction fallback) {
        this.fallbackFunction = fallback;
        return this;
    }

    public <T> T execute(SupplierWithThrowable<T> supplier, Class<T> returnType) {
        if (state == CircuitState.OPEN) {
            if (System.currentTimeMillis() - openTime > config.getOpenDurationSeconds() * 1000L) {
                log.info("熔断器 [{}] 进入半开状态", resourceName);
                state = CircuitState.HALF_OPEN;
                halfOpenSuccessCount = 0;
                halfOpenRequestCount = 0;
            } else {
                log.warn("熔断器 [{}] 已开启，执行降级", resourceName);
                return executeFallback(null, returnType);
            }
        }

        long startTime = System.currentTimeMillis();
        boolean success = false;
        T result = null;
        Exception exception = null;

        try {
            result = supplier.get();
            success = true;
            return result;
        } catch (Exception e) {
            exception = e;
            success = false;
            log.warn("熔断器 [{}] 调用失败: {}", resourceName, e.getMessage());
            return executeFallback(exception, returnType);
        } finally {
            long responseTime = System.currentTimeMillis() - startTime;
            record(success, responseTime, exception);
        }
    }

    private void record(boolean success, long responseTime, Exception e) {
        slidingWindow.record(success, responseTime);

        if (state == CircuitState.CLOSED) {
            checkAndOpen();
        } else if (state == CircuitState.HALF_OPEN) {
            if (success) {
                halfOpenSuccessCount++;
                halfOpenRequestCount++;
                if (halfOpenSuccessCount >= config.getHalfOpenMaxRequests()) {
                    log.info("熔断器 [{}] 探测成功，关闭熔断", resourceName);
                    state = CircuitState.CLOSED;
                }
            } else {
                halfOpenRequestCount++;
                if (halfOpenRequestCount >= config.getHalfOpenMaxRequests()) {
                    log.warn("熔断器 [{}] 探测失败，重新开启熔断", resourceName);
                    state = CircuitState.OPEN;
                    openTime = System.currentTimeMillis();
                    halfOpenSuccessCount = 0;
                    halfOpenRequestCount = 0;
                }
            }
        }
    }

    private void checkAndOpen() {
        SlidingWindow.StatData stat = slidingWindow.getStatData();

        if (stat.totalCount < config.getMinRequestAmount()) {
            return;
        }

        boolean shouldOpen = false;
        String reason = "";

        switch (config.getStrategy()) {
            case ERROR_RATE:
                if (stat.errorRate >= config.getErrorRateThreshold()) {
                    shouldOpen = true;
                    reason = String.format("错误率=%.2f%% >= 阈值=%.2f%%",
                        stat.errorRate * 100, config.getErrorRateThreshold() * 100);
                }
                break;
            case SLOW_CALL_RATIO:
                long slowCount = stat.maxResponseTime > config.getSlowCallMs() ? stat.totalCount : 0;
                double slowRatio = stat.totalCount > 0 ? (double) slowCount / stat.totalCount : 0;
                if (slowRatio >= config.getSlowCallRatioThreshold()) {
                    shouldOpen = true;
                    reason = String.format("慢调用比例=%.2f%% >= 阈值=%.2f%%",
                        slowRatio * 100, config.getSlowCallRatioThreshold() * 100);
                }
                break;
        }

        if (shouldOpen) {
            log.warn("熔断器 [{}] 开启！原因: {}, 统计: {}", resourceName, reason, stat);
            state = CircuitState.OPEN;
            openTime = System.currentTimeMillis();
        } else {
            log.debug("熔断器 [{}] 状态正常, 统计: {}", resourceName, stat);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T executeFallback(Exception e, Class<T> returnType) {
        if (fallbackFunction != null) {
            return (T) fallbackFunction.apply(e);
        }
        log.warn("熔断器 [{}] 未配置降级方法，返回 null", resourceName);
        return null;
    }

    public CircuitState getState() {
        return state;
    }

    public SlidingWindow.StatData getStatData() {
        return slidingWindow.getStatData();
    }
}