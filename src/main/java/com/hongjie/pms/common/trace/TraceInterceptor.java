package com.hongjie.pms.common.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class TraceInterceptor implements HandlerInterceptor {
    
    private static final String TRACE_HEADER = "X-Trace-Id";
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                             HttpServletResponse response, 
                             Object handler) {
        // 1. 从请求头获取 TraceId
        String traceId = request.getHeader(TRACE_HEADER);
        
        // 2. 没有则生成新的
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceContext.generateTraceId();
        }
        
        // 3. 存入 ThreadLocal
        TraceContext.setTraceId(traceId);
        
        // 4. 响应头返回，方便前端/下游
        response.setHeader(TRACE_HEADER, traceId);
        
        // 5. 开始根 Span
        String uri = request.getMethod() + " " + request.getRequestURI();
        TraceContext.startSpan(uri);
        
        log.info("Trace开始: traceId={}, uri={}", traceId, uri);
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // 结束当前 Span
        TraceContext.endSpan();
        
        // 清理 ThreadLocal
        TraceContext.clear();
    }
}