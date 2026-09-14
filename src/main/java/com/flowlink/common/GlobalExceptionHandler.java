package com.flowlink.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/** 全局异常处理：业务异常按错误码映射 HTTP 状态，未知异常统一 500。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.httpStatus())
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.httpStatus())
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "请求体无法解析：" + ex.getMostSpecificCause().getMessage()));
    }

    /** 缺少必填请求参数（@RequestParam(required=true)）。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.httpStatus())
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "缺少必填参数：" + ex.getParameterName()));
    }

    /** 路径/参数类型不匹配（如把字符串填进 long version）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.httpStatus())
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST,
                        "参数类型不匹配：" + ex.getName() + "=" + ex.getValue()));
    }

    /** HTTP 方法不被支持（如对只支持 POST 的路径用 GET）。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED, "不支持的请求方法：" + ex.getMethod()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }

    private String describe(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
