package com.miguelcortes.paymentgateway.application.pagination;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public PageResult {
        if (items == null) {
            throw new IllegalArgumentException("Items list must not be null");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must be greater than 0");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("Total elements must not be negative");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("Total pages must not be negative");
        }
        items = List.copyOf(items);
    }
}
