package com.hongjie.pms.common.circuitbreaker;

import lombok.Data;

@Data
public class CircuitBreakerConfig {
    
    private int windowSize = 10;
    private int bucketCount = 10;
    private int minRequestAmount = 5;
    private double errorRateThreshold = 0.5;
    private long slowCallMs = 1000;
    private double slowCallRatioThreshold = 0.5;
    private int openDurationSeconds = 10;
    private int halfOpenMaxRequests = 3;
    
    public enum Strategy {
        ERROR_RATE,
        SLOW_CALL_RATIO
    }
    private Strategy strategy = Strategy.ERROR_RATE;
}