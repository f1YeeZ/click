package com.clicker.mousehub.common;

import java.util.Map;

public record ApiError(ErrorBody error) {
    public record ErrorBody(String code, String message, Map<String, String> fields) {}

    public static ApiError of(String code, String message) {
        return new ApiError(new ErrorBody(code, message, Map.of()));
    }
}
