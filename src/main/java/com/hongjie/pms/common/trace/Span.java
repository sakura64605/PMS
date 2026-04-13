package com.hongjie.pms.common.trace;

import lombok.Data;
import com.alibaba.fastjson2.JSON;

@Data
public class Span {
    
    private String traceId;      // 全局追踪ID
    private String spanId;       // 当前跨度ID
    private String parentSpanId; // 父跨度ID
    private String spanName;     // 跨度名称
    private long startTime;      // 开始时间
    private long endTime;        // 结束时间
    private long duration;       // 耗时(ms)
    private String tags;         // 标签
    private String logs;         // 日志
    
    private static int spanCounter = 0;
    
    public Span(String spanName, String traceId, long startTime) {
        this.spanId = generateSpanId();
        this.spanName = spanName;
        this.traceId = traceId;
        this.startTime = startTime;
    }
    
    private synchronized String generateSpanId() {
        return String.valueOf(++spanCounter);
    }
    
    public String toJson() {
        return JSON.toJSONString(this);
    }
    
    @Override
    public String toString() {
        return String.format("[TraceId=%s, SpanId=%s, ParentSpanId=%s, Name=%s, Duration=%dms]",
            traceId, spanId, parentSpanId, spanName, duration);
    }
}