package com.hongjie.pms.common.util;

import com.hongjie.pms.common.exception.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Slf4j
@Data
@Component
public class JWTUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private Long expire; // 过期时间，单位：秒

    @Value("${jwt.header}")
    private String header;

    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    /**
     * 获取签名密钥
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT令牌
     *
     * @param userName 用户ID
     * @param role 用户角色
     * @return JWT令牌
     */
    public String generateToken(String userName, Integer role, Long userId) {

        Map<String, Object> claims = new HashMap<>();

        claims.put("userId", userId);
        claims.put("userName", userName);
        claims.put("role", role);

        return createToken(claims);
    }

    /**
     * 创建JWT令牌
     *
     * @param claims 令牌 claims
     * @return JWT令牌
     */
    private String createToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expire * 1000);

        return Jwts.builder()
                .setHeaderParams(Map.of("typ", "JWT"))
                .setHeaderParams(Map.of("alg", "HS256"))
                .setId(UUID.randomUUID().toString())
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 获取用户ID
     *
     * @param token JWT令牌
     * @return 用户ID
     */
    public String getUserName(String token) {
        Claims claims = parseToken(token);
        return (String) claims.get("userName");
    }

    /**
     * 获取角色
     *
     * @param token JWT令牌
     * @return 角色
     */
    public Integer getRole(String token) {
        Claims claims = parseToken(token);
        return (Integer) claims.get("role");
    }

    /**
     * 解析JWT令牌
     *
     * @param token JWT令牌
     * @return Claims
     */
    private Claims parseToken(String token) {
        try{
            // 去除Bearer前缀
            if (token != null && token.startsWith(tokenPrefix)) {
                token = token.substring(tokenPrefix.length());
            }

            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.error("JWT令牌已过期: {}", e.getMessage());
            throw new BusinessException(401, "token已过期");
        } catch (UnsupportedJwtException e) {
            log.error("JWT令牌格式不支持: {}", e.getMessage());
            throw new JwtException("token格式不支持");
        } catch (MalformedJwtException e) {
            log.error("JWT令牌格式错误: {}", e.getMessage());
            throw new JwtException("token格式错误");
        } catch (SignatureException e) {
            log.error("JWT签名验证失败: {}", e.getMessage());
            throw new JwtException("token签名验证失败");
        } catch (IllegalArgumentException e) {
            log.error("JWT令牌参数非法: {}", e.getMessage());
            throw new JwtException("token参数非法");
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userIdObj = claims.get("userId");

        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();  // Integer → Long
        } else if (userIdObj instanceof Long) {
            return (Long) userIdObj;                    // Long → Long
        } else {
            log.error("userId类型错误: {}", userIdObj.getClass());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
