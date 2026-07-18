package com.clicker.mousehub.common;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
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

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unknown(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of("INTERNAL_ERROR", "服务暂时不可用"));
    }
}
