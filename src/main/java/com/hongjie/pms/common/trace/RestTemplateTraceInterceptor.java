package com.hongjie.pms.common.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

@Slf4j
public class RestTemplateTraceInterceptor implements ClientHttpRequestInterceptor {
    
    private static final String TRACE_HEADER = "X-Trace-Id";
    
    @Override
    public ClientHttpResponse intercept(HttpRequest request, 
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String traceId = TraceContext.getTraceId();
        
        // 传递 TraceId 到下游服务
        if (traceId != null) {
            request.getHeaders().add(TRACE_HEADER, traceId);
        }
        
        // 开始子 Span
        String spanName = "HTTP: " + request.getMethod() + " " + request.getURI();
        TraceContext.startSpan(spanName);
        
        long start = System.currentTimeMillis();
        try {
            return execution.execute(request, body);
        } finally {
            long cost = System.currentTimeMillis() - start;
            TraceContext.endSpan();
            log.info("HTTP调用完成: uri={}, cost={}ms, traceId={}", 
                request.getURI(), cost, traceId);
        }
    }
}