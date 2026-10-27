package com.hongjie.pms.shared.core.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式登录校验。
 *
 * <p>白名单路径在 {@code AuthInterceptor} 中会被放行并允许「可选登录」，
 * 若某个 v2 端点在白名单路径下但业务上必须有登录态，用它显式约束。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireLogin {}
