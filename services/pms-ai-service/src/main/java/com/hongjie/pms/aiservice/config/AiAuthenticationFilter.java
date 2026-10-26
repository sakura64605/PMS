package com.hongjie.pms.aiservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongjie.pms.aiservice.core.AiCoreClient;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.platform.pojo.CommonResult;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.RequiredArgsConstructor;
import java.io.IOException;

@Component @RequiredArgsConstructor
public class AiAuthenticationFilter extends OncePerRequestFilter {
    private final AiCoreClient core;
    private final ObjectMapper json;
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith("/pet-system/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        UserContext.clear();
        try {
            String token=request.getHeader("Authorization");
            if("/pet-system/ai/ws".equals(request.getRequestURI()) && token==null)token=request.getParameter("token");
            var actor=core.authenticate(token);
            if(actor==null)throw new BusinessException(401,"请先登录");
            UserContext.setUser(actor);
            chain.doFilter(request,response);
        } catch(BusinessException failure) {
            response.setStatus(failure.getCode()==401?401:failure.getCode()==403?403:503);
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(),CommonResult.error(failure.getCode(),failure.getMessage()));
        } finally {UserContext.clear();}
    }
}
