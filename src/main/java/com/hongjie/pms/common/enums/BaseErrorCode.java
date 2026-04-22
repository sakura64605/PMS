package com.hongjie.pms.common.enums;

public enum BaseErrorCode implements IErrorCode {

    // ========== 一级宏观错误码 客户端错误 ==========
    CLIENT_ERROR(100000, "用户端错误"),

    // ========== 二级宏观错误码 用户注册错误 ==========
    USER_REGISTER_ERROR(100010, "用户注册错误"),
    USER_NAME_VERIFY_ERROR(100011, "用户名校验失败"),
    USER_NAME_EXIST_ERROR(100012, "用户名已存在"),
    USER_NAME_SENSITIVE_ERROR(100013, "用户名包含敏感词"),
    USER_NAME_SPECIAL_CHARACTER_ERROR(100014, "用户名包含特殊字符"),
    PASSWORD_VERIFY_ERROR(100015, "密码校验失败"),
    PASSWORD_SHORT_ERROR(100016, "密码长度不够"),
    PHONE_VERIFY_ERROR(100017, "手机格式校验失败"),

    // ========== 二级宏观错误码 系统请求缺少幂等Token ==========
    IDEMPOTENT_TOKEN_NULL_ERROR(100020, "幂等Token为空"),
    IDEMPOTENT_TOKEN_DELETE_ERROR(100021, "幂等Token已被使用或失效"),

    // ========== 一级宏观错误码 系统执行出错 ==========
    SERVICE_ERROR(100001, "系统执行出错"),
    // ========== 二级宏观错误码 系统执行超时 ==========
    SERVICE_TIMEOUT_ERROR(100010, "系统执行超时"),

    // ========== 一级宏观错误码 调用第三方服务出错 ==========
    REMOTE_ERROR(100002, "调用第三方服务出错");

    private final Integer code;

    private final String message;

    BaseErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
