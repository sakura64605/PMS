package com.hongjie.pms.shared.core.security;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link RequireRole} / {@link RequireLogin} 的切面实现。
 *
 * <p>校验依据是 {@code AuthInterceptor} 已经写入 {@link UserContext} 的登录态，
 * 不额外查库，因此单次校验是纯内存操作。
 */
@Slf4j
@Aspect
@Component
public class RoleCheckAspect {

    @Around("@annotation(requireRole)")
    public Object checkMethodRole(ProceedingJoinPoint pjp, RequireRole requireRole)
            throws Throwable {
        verifyRole(requireRole);
        return pjp.proceed();
    }

    @Around("@within(requireRole)")
    public Object checkClassRole(ProceedingJoinPoint pjp, RequireRole requireRole)
            throws Throwable {
        verifyRole(requireRole);
        return pjp.proceed();
    }

    @Around("@annotation(requireLogin)")
    public Object checkMethodLogin(ProceedingJoinPoint pjp, RequireLogin requireLogin)
            throws Throwable {
        verifyLogin();
        return pjp.proceed();
    }

    @Around("@within(requireLogin)")
    public Object checkClassLogin(ProceedingJoinPoint pjp, RequireLogin requireLogin)
            throws Throwable {
        verifyLogin();
        return pjp.proceed();
    }

    private void verifyRole(RequireRole requireRole) {
        Integer role = UserContext.getRole();
        // 未登录时也抛 FORBIDDEN，而不是 UNAUTHORIZED —— 这是刻意的。
        // 被替代的老代码写法是 if (!UserContext.isAdmin()) throw FORBIDDEN，
        // 未登录时 isAdmin 同样为 false，因此老的失败码一律是 403。
        // 换成 401 语义上更准确（前端可能据此跳登录页），但那属于对外行为变化，
        // 不该混在重构里做。若将来要改，应当是一次独立的、前后端一起评估的调整。
        for (int allowed : requireRole.value()) {
            if (role != null && allowed == role) {
                return;
            }
        }
        log.warn("角色校验失败: 当前角色={}, 需要={}", role, requireRole.value());
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void verifyLogin() {
        if (!UserContext.isLogin()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
