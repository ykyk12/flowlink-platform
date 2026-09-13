package com.flowlink.common;

import org.slf4j.MDC;

/** 统一响应体；traceId 自动取自 MDC，便于与日志/审计串联。 */
public record ApiResponse<T>(boolean success, String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ErrorCode.OK.name(), ErrorCode.OK.defaultMessage(), data, traceId());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, ErrorCode.OK.name(), ErrorCode.OK.defaultMessage(), null, traceId());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, code.name(), message == null ? code.defaultMessage() : message, null, traceId());
    }

    private static String traceId() {
        return MDC.get(TraceIdFilter.TRACE_ID);
    }
}
