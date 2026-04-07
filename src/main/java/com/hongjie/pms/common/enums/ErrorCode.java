package com.hongjie.pms.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    
    // 通用错误 (1000-1999)
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    MEDIA_TYPE_NOT_SUPPORTED(415, "不支持的媒体类型"),
    
    // 业务错误 (2000-2999)
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_DISABLED(2002, "账号已被禁用"),
    PASSWORD_ERROR(2003, "密码错误"),
    USERNAME_EXISTS(2004, "用户名已存在"),
    PHONE_EXISTS(2005, "手机号已注册"),
    EMAIL_EXISTS(2006, "邮箱已注册"),
    
    // 宠物模块 (3000-3999)
    PET_NOT_FOUND(3001, "宠物信息不存在"),
    PET_STATUS_ERROR(3002, "宠物状态错误"),
    PET_NO_PERMISSION(3003, "无权操作该宠物"),
    
    // 活动模块 (4000-4999)
    ACTIVITY_NOT_FOUND(4001, "活动不存在"),
    ACTIVITY_FULL(4002, "活动人数已满"),
    ACTIVITY_NOT_START(4003, "活动未开始"),
    ACTIVITY_ENDED(4004, "活动已结束"),
    ACTIVITY_SIGNUP_EXISTS(4005, "您已报名该活动"),
    ACTIVITY_NOT_SIGNUP(4006, "您未报名该活动"),
    
    // 文件上传 (5000-5999)
    FILE_TOO_LARGE(5001, "文件过大"),
    FILE_TYPE_ERROR(5002, "文件类型不支持"),
    UPLOAD_FAIL(5003, "文件上传失败"),
    
    // 系统错误 (9000-9999)
    DB_ERROR(9001, "数据库异常"),
    NETWORK_ERROR(9002, "网络异常"),
    THIRD_PARTY_ERROR(9003, "第三方服务异常");
    
    private final Integer code;
    private final String message;
    
    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}