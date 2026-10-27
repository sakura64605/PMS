package com.hongjie.pms.platform.exception;

import com.hongjie.pms.platform.enums.BaseErrorCode;
import com.hongjie.pms.platform.enums.IErrorCode;

public class ClientException extends AbstractException {

    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ClientException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "ClientException{"
                + "code='"
                + errorCode
                + "',"
                + "message='"
                + errorMessage
                + "'"
                + '}';
    }
}
