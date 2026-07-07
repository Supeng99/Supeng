package com.ai.assistant.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * 统一管理业务错误码，便于追踪和分类
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误 4xx
    PARAM_MISSING(4001, "缺少必需参数"),
    PARAM_VALID_ERROR(4002, "参数校验失败"),
    PARAM_BIND_ERROR(4003, "参数绑定失败"),
    PARAM_TYPE_ERROR(4004, "参数类型错误"),
    INVALID_REQUEST(4005, "无效的请求"),
    METHOD_NOT_ALLOWED(405, "不支持的请求方法"),

    // 认证授权 401
    UNAUTHORIZED(401, "未登录或登录已过期"),
    TOKEN_INVALID(4011, "Token无效"),
    TOKEN_EXPIRED(4012, "Token已过期"),
    PERMISSION_DENIED(403, "没有权限访问该资源"),

    // 资源相关 404
    NOT_FOUND(404, "资源不存在"),
    SESSION_NOT_FOUND(4041, "会话不存在"),
    MESSAGE_NOT_FOUND(4042, "消息不存在"),
    DOCUMENT_NOT_FOUND(4043, "文档不存在"),

    // 业务错误 5xx
    FILE_TOO_LARGE(5001, "文件大小超过限制"),
    FILE_TYPE_NOT_SUPPORT(5002, "不支持的文件类型"),
    FILE_UPLOAD_FAILED(5003, "文件上传失败"),
    FILE_PROCESS_FAILED(5004, "文件处理失败"),

    // AI服务错误 510
    AI_SERVICE_ERROR(5101, "AI服务调用失败"),
    AI_SERVICE_UNAVAILABLE(5102, "AI服务暂不可用"),
    AI_MODEL_NOT_FOUND(5103, "AI模型不存在"),
    AI_RESPONSE_TIMEOUT(5104, "AI响应超时"),
    AI_RESPONSE_EMPTY(5105, "AI返回内容为空"),

    // 会话错误 520
    SESSION_CREATE_FAILED(5201, "创建会话失败"),
    SESSION_UPDATE_FAILED(5202, "更新会话失败"),
    SESSION_DELETE_FAILED(5203, "删除会话失败"),
    SESSION_LIMIT_EXCEEDED(5204, "会话数量超过限制"),

    // 限流错误 530
    RATE_LIMIT_EXCEEDED(5301, "请求过于频繁"),
    IP_BLOCKED(5302, "IP已被限制访问"),

    // 系统错误 500
    INTERNAL_SERVER_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用");

    private final int code;
    private final String message;
}
