package com.hongjie.pms.modules.identity.user.application;

import com.hongjie.pms.platform.utils.JWTUtils;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.cache.AfterCommitCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserSessions {
    private final JWTUtils jwt;
    private final UserMapper users;
    private final StringRedisTemplate redis;

    public String issue(User user) {
        return jwt.generateToken(user.getUserName(), user.getRole(), user.getId(), version(user));
    }

    public UserInfo authenticate(String token) {
        if (token == null || !jwt.validateToken(token)) return null;
        User user = users.selectById(jwt.getUserId(token));
        if (user == null || !Objects.equals(user.getStatus(), 1)) return null;
        String credential = jwt.getCredentialVersion(token);
        if (credential != null
                && !MessageDigest.isEqual(
                        credential.getBytes(StandardCharsets.UTF_8),
                        version(user).getBytes(StandardCharsets.UTF_8))) return null;
        if (credential == null
                && "1".equals(redis.opsForValue().get("user:legacy-revoked:" + user.getId())))
            return null;
        if ("1".equals(redis.opsForValue().get(revokedKey(token)))) return null;
        // Authorization follows current database role, never an outdated JWT role.
        return new UserInfo(
                user.getId(),
                user.getUserName(),
                user.getRole(),
                user.getNickName(),
                user.getAvatar());
    }

    public void logout(String token) {
        if (token != null && jwt.validateToken(token)) {
            long remaining =
                    Math.max(1, jwt.getExpiration(token).getTime() - System.currentTimeMillis());
            redis.opsForValue().set(revokedKey(token), "1", Duration.ofMillis(remaining));
        }
    }

    public void passwordChanged(Long id) {
        // New tokens bind to the password hash without relying on cache invalidation.
        // Old-format tokens need a compatibility revocation flag for their maximum remaining
        // lifetime.
        AfterCommitCache.evict(
                () ->
                        redis.opsForValue()
                                .set(
                                        "user:legacy-revoked:" + id,
                                        "1",
                                        Duration.ofSeconds(jwt.getExpire())));
    }

    private String version(User user) {
        return hash(jwt.getSecret() + "\n" + user.getPassword());
    }

    private String revokedKey(String token) {
        String raw =
                token.startsWith(jwt.getTokenPrefix())
                        ? token.substring(jwt.getTokenPrefix().length()).trim()
                        : token.trim();
        return "user:token-revoked:" + hash(raw);
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
