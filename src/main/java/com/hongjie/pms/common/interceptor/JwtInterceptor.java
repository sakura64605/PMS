package com.hongjie.pms.common.interceptor;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.util.JWTUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.hongjie.pms.common.pojo.UserInfo;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JWTUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String token = request.getHeader(jwtUtils.getHeader());
        if (token != null && jwtUtils.validateToken(token)) {

            Long userId = jwtUtils.getUserId(token);
            String userName = jwtUtils.getUserName(token);
            Integer role = jwtUtils.getRole(token);

            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(userId);
            userInfo.setUserName(userName);
            userInfo.setRole(role);
            UserContext.setUser(userInfo);

            log.debug("用户信息已存入ThreadLocal: {}", userInfo);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }

}
