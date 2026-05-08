package com.hongjie.pms.common.interceptor;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.utils.JWTUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 统一认证拦截器
 * 合并原 JwtInterceptor 和 LoginInterceptor 的功能：
 * 1. 白名单路径：直接放行，但若携带 token 仍解析用户信息（可选认证）
 * 2. 非白名单路径：强制认证，token 无效则拒绝
 * 3. 使用 AntPathMatcher 替代 startsWith，避免路径匹配漏洞
 *
 * @author Hongjie
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JWTUtils jwtUtils;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 公开接口白名单（不需要登录即可访问）
     * 使用 Ant 风格路径模式，支持通配符精确匹配
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            // 用户认证
            "/pet-system/user/login",
            "/pet-system/user/register",
            // 公开浏览接口
            "/pet-system/pet/list",
            "/pet-system/pet/{id}",
            "/pet-system/activity/list",
            "/pet-system/activity/detail/{id}",
            "/pet-system/notice/list",
            "/pet-system/notice/{id}",
            "/pet-system/daily/{id}",
            "/pet-system/daily/topics/hot",
            "/pet-system/daily/topics/search",
            "/pet-system/search/global",
            "/pet-system/search/suggest",
            // 系统路径
            "/error",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();
        String method = request.getMethod();
        log.debug("认证拦截: {} {}", method, uri);

        // 1. 判断是否为白名单路径
        boolean isPublic = isWhiteListed(uri);

        // 2. 尝试解析 token（无论是否白名单，携带 token 都解析用户信息）
        String token = request.getHeader(jwtUtils.getHeader());
        UserInfo userInfo = null;

        if (token != null && jwtUtils.validateToken(token)) {
            Long userId = jwtUtils.getUserId(token);
            String userName = jwtUtils.getUserName(token);
            Integer role = jwtUtils.getRole(token);

            userInfo = new UserInfo(userId, userName, role, null, null);
            UserContext.setUser(userInfo);
            log.debug("用户信息已存入ThreadLocal: userId={}, username={}", userId, userName);
        }

        // 3. 白名单路径直接放行（可选认证：无 token 也可访问）
        if (isPublic) {
            return true;
        }

        // 4. 非白名单路径：强制认证
        if (userInfo == null) {
            log.warn("未认证访问: {} {}", method, uri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        // 请求结束后清除 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

    /**
     * 使用 AntPathMatcher 匹配白名单
     * 比 startsWith 更安全，支持精确的通配符匹配
     */
    private boolean isWhiteListed(String uri) {
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }
}
