package com.clicker.mousehub.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, PageMeta page) {
    public record PageMeta(long number, long size, long totalItems, long totalPages) {}
}
