package com.ai.assistant.exception;

import com.ai.assistant.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理各类异常，返回标准化错误响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Object> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 - path: {}, code: {}, message: {}",
                request.getRequestURI(), e.getCode(), e.getMessage());

        ApiResponse<Object> response = ApiResponse.error(e.getCode(), e.getMessage());
        response.setData(e.getData());
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("参数校验异常 - path: {}", request.getRequestURI());

        StringBuilder sb = new StringBuilder();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(error.getField()).append(": ").append(error.getDefaultMessage());
        }
        return ApiResponse.error(ErrorCode.PARAM_VALID_ERROR.getCode(), sb.toString());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleBindException(BindException e, HttpServletRequest request) {
        log.warn("参数绑定异常 - path: {}", request.getRequestURI());

        StringBuilder sb = new StringBuilder();
        for (FieldError error : e.getFieldErrors()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(error.getField()).append(": ").append(error.getDefaultMessage());
        }
        return ApiResponse.error(ErrorCode.PARAM_BIND_ERROR.getCode(), sb.toString());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少请求参数 - path: {}, param: {}", request.getRequestURI(), e.getParameterName());
        String message = String.format("缺少必需参数: %s", e.getParameterName());
        return ApiResponse.error(ErrorCode.PARAM_MISSING.getCode(), message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("请求方法不支持 - path: {}, method: {}", request.getRequestURI(), e.getMethod());
        String message = String.format("不支持的请求方法: %s", e.getMethod());
        return ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED.getCode(), message);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Object> handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("资源不存在 - path: {}", request.getRequestURI());
        return ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), "接口不存在: " + request.getRequestURI());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("文件过大 - path: {}", request.getRequestURI());
        return ApiResponse.error(ErrorCode.FILE_TOO_LARGE.getCode(), "上传文件大小超过限制，请压缩后重试");
    }

    @ExceptionHandler(RateLimitException.class)
    public ApiResponse<Object> handleRateLimitException(RateLimitException e, HttpServletRequest request) {
        log.warn("触发限流 - path: {}", request.getRequestURI());
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), e.getMessage());
        Map<String, Object> data = new HashMap<>();
        data.put("retryAfter", e.getRetryAfter());
        response.setData(data);
        return response;
    }

    @ExceptionHandler(AiServiceException.class)
    public ApiResponse<Object> handleAiServiceException(AiServiceException e, HttpServletRequest request) {
        log.error("AI服务异常 - path: {}, model: {}", request.getRequestURI(), e.getModelType(), e);
        return ApiResponse.error(ErrorCode.AI_SERVICE_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Object> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常 - path: {}", request.getRequestURI(), e);
        return ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "系统繁忙，请稍后重试");
    }
}
