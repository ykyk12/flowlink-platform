package com.flowlink.common;

import org.slf4j.MDC;

/** 统一响应体；traceId 自动取自 MDC，便于与日志/审计串联。 */
public record ApiResponse<T>(boolean success, String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ErrorCode.OK.name(), ErrorCode.OK.defaultMessage(), data, currentTraceId());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, ErrorCode.OK.name(), ErrorCode.OK.defaultMessage(), null, currentTraceId());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, code.name(), message == null ? code.defaultMessage() : message, null, currentTraceId());
    }

    /** 注意：方法名不能叫 traceId()——record 组件的访问器必须 public，同名私有方法会被判定为非法访问器。 */
    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.TRACE_ID);
    }
}
