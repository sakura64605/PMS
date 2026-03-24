package com.hongjie.pms.common.base.core;

import com.hongjie.pms.common.pojo.UserInfo;

public class UserContext {

    private static final ThreadLocal<UserInfo> USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置用户信息
     */
    public static void setUser(UserInfo user) {
        USER_HOLDER.set(user);
    }

    /**
     * 获取用户信息
     */
    public static UserInfo getUser() {
        return USER_HOLDER.get();
    }

    /**
     * 获取用户ID
     */
    public static Long getUserId() {
        UserInfo user = USER_HOLDER.get();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 获取用户名
     */
    public static String getUserName() {
        UserInfo user = USER_HOLDER.get();
        return user != null ? user.getUserName() : null;
    }

    /**
     * 获取用户角色
     */
    public static Integer getRole() {
        UserInfo user = USER_HOLDER.get();
        return user != null ? user.getRole() : null;
    }

    /**
     * 判断是否登录
     */
    public static boolean isLogin() {
        return USER_HOLDER.get() != null;
    }

    /**
     * 判断是否是管理员
     */
    public static boolean isAdmin() {
        UserInfo user = USER_HOLDER.get();
        return user != null && user.getRole() == 1;
    }

    /**
     * 清空用户信息（防止内存泄漏）
     */
    public static void clear() {
        USER_HOLDER.remove();
    }

}
