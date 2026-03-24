package com.hongjie.pms.common.base;

import com.hongjie.pms.common.pojo.CommonResult;

public abstract class BaseController {

    public <T> CommonResult<T> success() {
        return CommonResult.success();
    }

    public <T> CommonResult<T> success(T data) {
        return CommonResult.success(data);
    }

    public <T> CommonResult<T> success(T data, String message) {
        return CommonResult.success(data, message);
    }

    public <T> CommonResult<T> error(Integer code, String message) {
        return CommonResult.error(code, message);
    }

    public <T> CommonResult<T> error(Integer code, String message, String exception) {
        return CommonResult.error(code, message, exception);
    }

}
