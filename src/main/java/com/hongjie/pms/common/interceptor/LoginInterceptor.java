package com.hongjie.pms.common.interceptor;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.util.JWTUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JWTUtils jwtUtils;

    // 白名单路径（不需要登录）
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/pet-system/user/login",
            "/pet-system/user/register",
            "/error",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();
        log.debug("请求路径: {}", uri);

        // 1. 检查白名单
        if (isWhiteList(uri)) {
            return true;
        }

        // 2. 获取token
        String token = request.getHeader(jwtUtils.getHeader());

        // 3. 验证token
        if (token == null || !jwtUtils.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
            return false;
        }

        // 4. 解析用户信息并存入ThreadLocal
        Long userId = jwtUtils.getUserId(token);
        String userName = jwtUtils.getUserName(token);
        Integer role = jwtUtils.getRole(token);

        UserInfo userInfo = new UserInfo(userId, userName, role, null, null);
        UserContext.setUser(userInfo);

        log.debug("用户登录验证通过: userId={}, username={}", userId, userName);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        // 请求结束后清除ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

    /**
     * 检查是否在白名单中
     */
    private boolean isWhiteList(String uri) {
        return WHITE_LIST.stream().anyMatch(uri::startsWith);
    }
}