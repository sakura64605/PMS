package com.hongjie.pms.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode implements IErrorCode{
    
    // 通用错误 (1000-1999)
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "请求参数有误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "内容不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    MEDIA_TYPE_NOT_SUPPORTED(415, "不支持的媒体类型"),

    IDEMPOTENT_TOKEN_NULL_ERROR(400, "请勿重复提交"),
    IDEMPOTENT_TOKEN_DELETE_ERROR(400, "请勿重复提交"),

    // 限流错误 1020-1029
    RATE_LIMIT(1020, "请求过于频繁，请稍后再试"),

    // 熔断降级错误 1030-1039
    CIRCUIT_BREAKER_OPEN(1030, "服务繁忙，请稍后再试"),
    CIRCUIT_BREAKER_FALLBACK(1031, "系统繁忙，正在恢复中"),

    // 权限错误 1040-1049
    NO_PERMISSION(1040, "无权限"),

    // 验证码错误 1050-1059
    CAPTCHA_ERROR(1050, "验证码错误"),
    CAPTCHA_EXPIRED(1051, "验证码已过期"),

    // 邮件错误 1060-1069
    EMAIL_SEND_FAIL(1060, "邮件发送失败"),
    EMAIL_NOT_FOUND(1061, "邮箱不存在"),

    // 审核错误 1070-1079
    AUDIT_NOT_FOUND(1070, "审核信息不存在"),
    AUDIT_REJECT(1071, "未通过审核"),
    AUDIT_WAITING(1072, "待审核"),
    AUDIT_APPROVE(1073, "已通过审核"),

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

    // 消息队列错误 6000-6099
    MQ_SEND_FAIL(6001, "消息发送失败，请稍后再试"),
    MQ_CONSUME_FAIL(6002, "消息处理失败"),

    // OSS错误 7000-7099
    OSS_UPLOAD_FAIL(7001, "文件上传失败，请稍后再试"),
    OSS_DELETE_FAIL(7002, "文件删除失败"),
    
    // 系统错误 (9000-9999)
    DB_ERROR(9001, "系统繁忙，请稍后再试"),
    NETWORK_ERROR(9002, "网络异常"),
    SYSTEM_ERROR(9999, "系统繁忙，请稍后再试"),
    THIRD_PARTY_ERROR(9003, "服务暂时不可用，请稍后再试");
    
    private final Integer code;
    private final String message;
    
    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}