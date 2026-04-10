package com.hongjie.pms.common.pojo;

import com.hongjie.pms.common.enums.ErrorCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class CommonResult<T> implements Serializable {

    /**
	 * 错误码
	 */
    private Integer code;

    /**
	 * 错误信息
	 */
    private String message;

    /**
	 * 数据结果
	 */
    private T data;

    /**
	 * 异常信息
	 */
    public String exception;

    public static <T> CommonResult<T> success() {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(200);
        result.setMessage("操作成功");
        return result;
    }

    public static <T> CommonResult<T> success(T data) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> CommonResult<T> success(T data, String message) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> CommonResult<T> error(Integer code, String message) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> CommonResult<T> error(Integer code, String message, String exception) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setException(exception);
        return result;
    }

    /**
     * 使用错误码枚举返回错误
     */
    public static <T> CommonResult<T> error(ErrorCode errorCode) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(errorCode.getCode());
        result.setMessage(errorCode.getMessage());
        return result;
    }

    /**
     * 使用错误码枚举返回错误（自定义消息）
     */
    public static <T> CommonResult<T> error(ErrorCode errorCode, String message) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(errorCode.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 限流错误快捷方法
     */
    public static <T> CommonResult<T> rateLimit() {
        return error(ErrorCode.RATE_LIMIT);
    }

    /**
     * 限流错误快捷方法（自定义消息）
     */
    public static <T> CommonResult<T> rateLimit(String message) {
        return error(ErrorCode.RATE_LIMIT, message);
    }

}
