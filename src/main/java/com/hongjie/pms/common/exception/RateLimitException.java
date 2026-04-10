package com.hongjie.pms.common.exception;

import lombok.Getter;

/**
 * 限流异常
 * 
 * @author hongjie
 */
@Getter
public class RateLimitException extends RuntimeException {
    
    /**
     * 请求URI
     */
    private final String requestUri;
    
    /**
     * 限流Key
     */
    private final String limitKey;
    
    public RateLimitException(String message) {
        super(message);
        this.requestUri = "";
        this.limitKey = "";
    }
    
    public RateLimitException(String message, String requestUri) {
        super(message);
        this.requestUri = requestUri;
        this.limitKey = "";
    }
    
    public RateLimitException(String message, String requestUri, String limitKey) {
        super(message);
        this.requestUri = requestUri;
        this.limitKey = limitKey;
    }
}