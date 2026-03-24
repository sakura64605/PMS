package com.hongjie.pms.common.config;

import com.hongjie.pms.common.interceptor.JwtInterceptor;
import com.hongjie.pms.common.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final LoginInterceptor loginInterceptor;

    /**
     * 注册拦截器 - 按顺序执行
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 方式1：分别注册（推荐）
        // 先执行登录验证，再执行JWT解析（如果有需要）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/pet-system/user/login",
                        "/pet-system/user/register",
                        "/error"
                )
                .order(1);  // 优先级数字越小越先执行

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/pet-system/user/login",
                        "/pet-system/user/register",
                        "/error",
                        "/swagger-ui/**",
                        "/v3/**"
                )
                .order(2);  // 在loginInterceptor之后执行
    }
}