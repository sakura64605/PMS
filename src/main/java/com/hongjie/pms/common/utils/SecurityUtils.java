package com.hongjie.pms.common.utils;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;

/**
 * 安全工具类 - 基于 UserContext
 */
public class SecurityUtils {
    
    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        return UserContext.getUserId();
    }
    
    /**
     * 获取当前用户信息
     */
    public static UserInfo getCurrentUser() {
        return UserContext.getUser();
    }
    
    /**
     * 获取当前用户名
     */
    public static String getCurrentUserName() {
        return UserContext.getUserName();
    }
    
    /**
     * 获取当前用户角色
     */
    public static Integer getCurrentUserRole() {
        return UserContext.getRole();
    }
    
    /**
     * 检查是否已登录
     */
    public static boolean isLogin() {
        return UserContext.isLogin();
    }
    
    /**
     * 检查是否是管理员
     */
    public static boolean isAdmin() {
        return UserContext.isAdmin();
    }
    
    /**
     * 获取当前用户ID，未登录则抛出异常
     */
    public static Long getCurrentUserIdOrThrow() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
    
    /**
     * 检查当前用户是否是管理员，不是则抛出异常
     */
    public static void checkAdmin() {
        if (!isAdmin()) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }
}