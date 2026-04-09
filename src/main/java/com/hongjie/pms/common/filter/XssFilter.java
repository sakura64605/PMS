package com.hongjie.pms.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Deprecated
@Slf4j
@Order(1)  // 优先级最高，最先执行
public class XssFilter implements Filter {

    // 排除过滤的路径
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/login",
            "/register",
            "/upload",
            "/avatar/upload"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        log.info("=== XssFilter 执行了！路径: {} ===", path);
        // 排除不需要过滤的路径
        if (isExcludePath(path)) {
            log.debug("XSS过滤跳过: {}", path);
            chain.doFilter(request, response);
            return;
        }

        // 使用 XSS 包装器
        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(httpRequest);
        log.debug("XSS过滤启用: {}", path);
        chain.doFilter(xssRequest, response);
    }

    /**
     * 判断是否需要排除
     */
    private boolean isExcludePath(String path) {
        return EXCLUDE_PATHS.stream().anyMatch(path::contains);
    }
}