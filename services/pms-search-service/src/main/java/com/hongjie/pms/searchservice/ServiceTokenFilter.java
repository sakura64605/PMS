package com.hongjie.pms.searchservice;

import com.hongjie.pms.contracts.search.SearchProtocol;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ServiceTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public ServiceTokenFilter(SearchServiceProperties properties) {
        expected = properties.token().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/")) {
            String supplied = request.getHeader(SearchProtocol.TOKEN_HEADER);
            if (supplied == null
                    || !MessageDigest.isEqual(
                            expected, supplied.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
