package com.hongjie.pms.bootstrap.ai;

import com.hongjie.pms.ai.contract.*;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.platform.pojo.*;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.identity.user.application.UserSessions;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivitySignupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Trusted service endpoint. Never expose this namespace through the public gateway. */
@RestController
@RequestMapping("/internal/ai/v1")
@ConditionalOnProperty(name = "pms.ai.core.enabled", havingValue = "true")
public class AiCoreController {
    private final byte[] secret;
    private final UserSessions sessions;
    private final UserMapper users;
    private final PetPostMapper posts;
    private final ActivitySignupMapper signups;
    private final Map<String, CoreQueryTool> tools;

    public AiCoreController(
            @Value("${pms.ai.core.service-token:${PMS_AI_CORE_SERVICE_TOKEN:}}") String secret,
            UserSessions sessions,
            UserMapper users,
            PetPostMapper posts,
            ActivitySignupMapper signups,
            List<CoreQueryTool> tools) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException(
                    "AI internal service token must contain at least 32 bytes");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.sessions = sessions;
        this.users = users;
        this.posts = posts;
        this.signups = signups;
        this.tools =
                tools.stream()
                        .collect(Collectors.toUnmodifiableMap(CoreQueryTool::getName, t -> t));
    }

    public record Authentication(String token) {}

    public record ToolRequest(Long actor, Map<String, Object> arguments) {}

    @PostMapping("/authenticate")
    public CommonResult<UserInfo> authenticate(
            @RequestHeader(value = "X-PMS-Service-Token", defaultValue = "") String key,
            @RequestBody Authentication request) {
        check(key);
        var identity = sessions.authenticate(request.token());
        if (identity == null) throw new BusinessException(401, "请重新登录");
        return CommonResult.success(identity);
    }

    @PostMapping("/tools/{name}")
    public CommonResult<ToolCall> tool(
            @RequestHeader(value = "X-PMS-Service-Token", defaultValue = "") String key,
            @PathVariable String name,
            @RequestBody ToolRequest request) {
        check(key);
        requireUser(request.actor());
        CoreQueryTool tool = tools.get(name);
        if (tool == null) throw new BusinessException(400, "不支持的业务查询工具");
        return CommonResult.success(
                tool.execute(
                        request.arguments() == null ? Map.of() : request.arguments(),
                        request.actor()));
    }

    @GetMapping("/profile/{actor}")
    public CommonResult<AiUserProfile> profile(
            @RequestHeader(value = "X-PMS-Service-Token", defaultValue = "") String key,
            @PathVariable Long actor) {
        check(key);
        var user = requireUser(actor);
        return CommonResult.success(
                new AiUserProfile(
                        user.getNickName() == null ? user.getUserName() : user.getNickName(),
                        user.getSignature(),
                        posts.selectCount(
                                new LambdaQueryWrapper<PetPost>().eq(PetPost::getUserId, actor)),
                        signups.selectCount(
                                new LambdaQueryWrapper<ActivitySignup>()
                                        .eq(ActivitySignup::getUserId, actor)
                                        .eq(ActivitySignup::getStatus, 1))));
    }

    private com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User
            requireUser(Long actor) {
        if (actor == null || actor <= 0) throw new BusinessException(400, "无效用户");
        var user = users.selectById(actor);
        if (user == null || !Objects.equals(user.getStatus(), 1))
            throw new BusinessException(401, "账号不可用");
        return user;
    }

    private void check(String provided) {
        if (!MessageDigest.isEqual(secret, provided.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(403, "无效的服务身份");
    }
}
