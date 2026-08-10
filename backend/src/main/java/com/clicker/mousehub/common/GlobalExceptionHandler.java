package com.clicker.mousehub.common;

import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiError.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        return ResponseEntity.badRequest().body(new ApiError(new ApiError.ErrorBody("VALIDATION_ERROR", "提交内容不符合要求", fields)));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiError> duplicate(DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("RESOURCE_CONFLICT", "数据已存在或发生唯一性冲突"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> illegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_ARGUMENT", "请求参数格式不正确"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiError> malformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_ARGUMENT", "请求参数格式不正确"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> resourceNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "请求的资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED", "该资源不支持此请求方法"));
    }

    @ExceptionHandler(IOException.class)
    ResponseEntity<Void> io(IOException exception) {
        if (isClientDisconnect(exception)) {
            log.debug("SSE client disconnected before the response completed");
            return ResponseEntity.noContent().build();
        }
        log.error("Unhandled I/O exception", exception);
        return ResponseEntity.internalServerError().build();
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    ResponseEntity<Void> asyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        log.debug("Async response is no longer usable");
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "不支持该请求内容类型"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unknown(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of("INTERNAL_ERROR", "服务暂时不可用"));
    }

    private static boolean isClientDisconnect(IOException exception) {
        String type = exception.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        String message = String.valueOf(exception.getMessage()).toLowerCase(java.util.Locale.ROOT);
        return type.contains("clientabort") || message.contains("broken pipe")
                || message.contains("connection reset") || message.contains("connection aborted");
    }
}
