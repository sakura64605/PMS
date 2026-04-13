package com.hongjie.pms.common.trace;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 链路追踪上下文（基于 ThreadLocal）
 */
public class TraceContext {
    
    private static final ThreadLocal<String> traceIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Deque<Span>> spanStackHolder = ThreadLocal.withInitial(ArrayDeque::new);
    
    /**
     * 生成新的 TraceId
     */
    public static String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 设置 TraceId
     */
    public static void setTraceId(String traceId) {
        traceIdHolder.set(traceId);
    }
    
    /**
     * 获取 TraceId
     */
    public static String getTraceId() {
        return traceIdHolder.get();
    }
    
    /**
     * 开始一个 Span
     */
    public static void startSpan(String spanName) {
        Deque<Span> stack = spanStackHolder.get();
        Span span = new Span(spanName, getTraceId(), System.currentTimeMillis());
        span.setParentSpanId(getCurrentSpanId());
        stack.push(span);
    }
    
    /**
     * 结束当前 Span
     */
    public static void endSpan() {
        Deque<Span> stack = spanStackHolder.get();
        if (!stack.isEmpty()) {
            Span span = stack.pop();
            span.setEndTime(System.currentTimeMillis());
            span.setDuration(span.getEndTime() - span.getStartTime());
            // 打印或存储 Span
            System.out.println(span.toJson());
        }
    }
    
    /**
     * 获取当前 SpanId
     */
    public static String getCurrentSpanId() {
        Deque<Span> stack = spanStackHolder.get();
        if (!stack.isEmpty()) {
            return stack.peek().getSpanId();
        }
        return null;
    }
    
    /**
     * 清理上下文
     */
    public static void clear() {
        traceIdHolder.remove();
        spanStackHolder.remove();
    }
}