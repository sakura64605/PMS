package com.hongjie.pms.searchservice;
import com.hongjie.pms.contracts.search.SearchProtocol;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component
public class SearchRequestMetrics extends OncePerRequestFilter {
    private final MeterRegistry metrics;
    private final String instance;
    public SearchRequestMetrics(MeterRegistry metrics,@Value("${pms.instance-id:${server.port}}") String instance){this.metrics=metrics;this.instance=instance;}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        String trace=org.slf4j.MDC.get("traceId");
        if(trace!=null)response.setHeader("X-Trace-Id",trace);
        try{chain.doFilter(request,response);}finally{
            if(request.getRequestURI().equals(SearchProtocol.PATH)){
                metrics.counter("pms.search.requests","instance",instance,"status",Integer.toString(response.getStatus())).increment();
                org.slf4j.LoggerFactory.getLogger(SearchRequestMetrics.class).info("Search retrieval served instance={} traceId={} status={}",instance,trace,response.getStatus());
            }
        }
    }
}
