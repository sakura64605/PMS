package com.hongjie.pms.common.config;

import com.hongjie.pms.common.trace.RestTemplateTraceInterceptor;
import com.hongjie.pms.common.trace.TraceInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TraceConfig implements WebMvcConfigurer {
    
    /**
     * 注册 Trace 拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceInterceptor())
                .addPathPatterns("/**")
                .order(3);
    }
    
    /**
     * 配置 RestTemplate（自动传递TraceId）
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.additionalInterceptors(new RestTemplateTraceInterceptor()).build();
    }
}