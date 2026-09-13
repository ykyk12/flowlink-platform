package com.flowlink.common;

/** 统一错误码与 HTTP 状态映射。 */
public enum ErrorCode {

    OK(200, "OK"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "缺少或无效的 API Key"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源状态冲突"),
    QUOTA_EXCEEDED(429, "超出配额，请稍后重试"),
    RULE_INVALID(422, "规则校验失败"),
    INTERNAL_ERROR(500, "服务内部错误");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
