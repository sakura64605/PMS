package com.hongjie.pms.refactor.core.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式角色校验。
 *
 * <p>替代老代码中散落的 24 处 {@code UserContext.isAdmin()} 手写判断
 * （{@code AdminController} 一个类里就重复了 5 次）。
 * 可标在方法或类上，标在类上即对该类所有方法生效。
 *
 * <p><b>为什么不直接用 Spring Security 的 {@code @PreAuthorize}</b>：
 * 本项目只引入了 {@code spring-security-crypto}（用于 BCrypt），没有 Security starter，
 * 也没有 SecurityContext 装配，认证链是自研 JWT + {@code AuthInterceptor} + {@code UserContext}。
 * 上全套 Spring Security 意味着重构整条认证链；本注解直接复用已有的 {@code UserContext}，零迁移成本。
 * 将来若需要 RBAC 或资源级鉴权，再换 {@code @PreAuthorize} —— 届时本注解的调用点即迁移清单。
 *
 * @see RoleCheckAspect
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许通过的角色码，默认仅管理员（role == 1） */
    int[] value() default {1};
}
