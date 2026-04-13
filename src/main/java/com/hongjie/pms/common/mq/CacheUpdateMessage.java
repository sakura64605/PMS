package com.hongjie.pms.common.mq;

import lombok.Data;
import java.io.Serializable;

@Data
public class CacheUpdateMessage implements Serializable {
    
    /** 操作类型：EVICT(清除), UPDATE(更新) */
    private String operation;
    
    /** 缓存名称 */
    private String cacheName;
    
    /** 缓存key */
    private String cacheKey;
    
    /** 是否清除所有 */
    private boolean allEntries;
    
    /** 延迟毫秒（用于延迟双删） */
    private Long delayMillis;
    
    private Long timestamp;
}