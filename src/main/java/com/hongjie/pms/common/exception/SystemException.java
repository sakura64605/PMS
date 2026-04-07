package com.hongjie.pms.common.exception;

import com.hongjie.pms.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class SystemException extends RuntimeException {
    
    private final Integer code;
    private final String message;
    
    public SystemException(String message) {
        super(message);
        this.code = ErrorCode.FAIL.getCode();
        this.message = message;
    }
    
    public SystemException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    
    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.FAIL.getCode();
        this.message = message;
    }
}