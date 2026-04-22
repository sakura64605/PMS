package com.hongjie.pms.common.circuitbreaker.config;

import com.hongjie.pms.common.circuitbreaker.CircuitBreakerManager;
import com.hongjie.pms.common.circuitbreaker.aspect.CircuitBreakerAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerAutoConfiguration {

    /**
     * 注册熔断切面
     */
    @Bean
    public CircuitBreakerAspect circuitBreakerAspect(CircuitBreakerManager circuitBreakerManager) {
        CircuitBreakerAspect aspect = new CircuitBreakerAspect();
        // 使用反射设置 breakerManager，因为 CircuitBreakerAspect 使用了 @Autowired
        try {
            java.lang.reflect.Field field = CircuitBreakerAspect.class.getDeclaredField("breakerManager");
            field.setAccessible(true);
            field.set(aspect, circuitBreakerManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return aspect;
    }
}