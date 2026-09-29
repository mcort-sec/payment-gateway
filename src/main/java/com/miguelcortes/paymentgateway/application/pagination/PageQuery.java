package com.miguelcortes.paymentgateway.application.pagination;

import com.miguelcortes.paymentgateway.application.exception.InvalidPaginationException;

public record PageQuery(int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new InvalidPaginationException("Page index must not be negative");
        }
        if (size <= 0 || size > MAX_SIZE) {
            throw new InvalidPaginationException("Page size must be between 1 and " + MAX_SIZE);
        }
    }
}
