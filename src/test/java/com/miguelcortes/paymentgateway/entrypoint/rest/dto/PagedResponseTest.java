package com.miguelcortes.paymentgateway.entrypoint.rest.dto;

import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagedResponseTest {

    @Test
    @DisplayName("Should correctly map PageResult to PagedResponse using mapper")
    void shouldMapPageResultToPagedResponse() {
        PageResult<Integer> pageResult = new PageResult<>(
                List.of(1, 2, 3),
                0,
                20,
                3L,
                1
        );

        PagedResponse<String> response = PagedResponse.from(pageResult, String::valueOf);

        assertEquals(List.of("1", "2", "3"), response.content());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(3L, response.totalElements());
        assertEquals(1, response.totalPages());
    }
}
