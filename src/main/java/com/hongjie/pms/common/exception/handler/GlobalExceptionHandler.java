package com.hongjie.pms.common.exception.handler;

import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.exception.RateLimitException;
import com.hongjie.pms.common.exception.SystemException;
import com.hongjie.pms.common.pojo.CommonResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public CommonResult<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return CommonResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(SystemException.class)
    public CommonResult<Void> handleSystemException(SystemException e) {
        log.error("系统异常: code={}, message={}", e.getCode(), e.getMessage(), e);
        return CommonResult.error(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return CommonResult.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    public CommonResult<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return CommonResult.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", message);
        return CommonResult.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public CommonResult<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = "缺少必要参数: " + e.getParameterName();
        log.warn(message);
        return CommonResult.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public CommonResult<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return CommonResult.error(ErrorCode.PARAM_ERROR.getCode(), "请求参数格式错误");
    }

    // ==================== 请求方式异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public CommonResult<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return CommonResult.error(ErrorCode.METHOD_NOT_ALLOWED.getCode(), "请求方法不支持，请使用 " + String.join(", ", e.getSupportedMethods()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public CommonResult<Void> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的媒体类型: {}", e.getMessage());
        return CommonResult.error(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED.getCode(), "不支持的媒体类型");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public CommonResult<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return CommonResult.error(ErrorCode.NOT_FOUND.getCode(), "接口不存在");
    }

    // ==================== 文件上传异常 ====================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public CommonResult<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件大小超限: {}", e.getMessage());
        return CommonResult.error(ErrorCode.FILE_TOO_LARGE.getCode(), "文件大小不能超过" + e.getMaxUploadSize() / 1024 / 1024 + "MB");
    }

    // ==================== 数据库异常 ====================

    @ExceptionHandler(SQLException.class)
    public CommonResult<Void> handleSQLException(SQLException e) {
        log.error("SQL异常: {}", e.getMessage(), e);
        return CommonResult.error(ErrorCode.DB_ERROR.getCode(), "数据库操作失败");
    }

    @ExceptionHandler(DataAccessException.class)
    public CommonResult<Void> handleDataAccessException(DataAccessException e) {
        log.error("数据访问异常: {}", e.getMessage(), e);
        return CommonResult.error(ErrorCode.DB_ERROR.getCode(), "数据访问失败");
    }

    // ==================== 其他异常 ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResult<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return CommonResult.error(ErrorCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public CommonResult<Void> handleIllegalStateException(IllegalStateException e) {
        log.error("非法状态: {}", e.getMessage(), e);
        return CommonResult.error(ErrorCode.FAIL.getCode(), e.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public CommonResult<Void> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常: ", e);
        return CommonResult.error(ErrorCode.FAIL.getCode(), "系统内部错误");
    }

    /**
     * 限流异常处理
     */
    @ExceptionHandler(RateLimitException.class)
    public CommonResult<Void> handleRateLimitException(RateLimitException e) {
        log.warn("限流拦截: {} - {}", e.getRequestUri(), e.getMessage());
        return CommonResult.rateLimit(e.getMessage());
    }

    // ==================== 兜底异常 ====================

    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleException(Exception e) {
        log.error("未捕获的系统异常: ", e);
        return CommonResult.error(ErrorCode.FAIL.getCode(), "系统繁忙，请稍后再试");
    }
}