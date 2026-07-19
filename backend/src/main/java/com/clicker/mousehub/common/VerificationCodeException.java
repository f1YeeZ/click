package com.clicker.mousehub.common;

import org.springframework.http.HttpStatus;

public class VerificationCodeException extends BusinessException {
    public VerificationCodeException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
