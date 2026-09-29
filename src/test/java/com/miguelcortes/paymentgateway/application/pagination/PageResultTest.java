package com.miguelcortes.paymentgateway.application.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageResultTest {

    @Test
    @DisplayName("Should create PageResult and maintain defensive copy of items")
    void shouldCreatePageResultWithDefensiveCopy() {
        List<String> mutableList = new ArrayList<>();
        mutableList.add("item1");
        mutableList.add("item2");

        PageResult<String> result = new PageResult<>(mutableList, 0, 10, 2L, 1);

        assertEquals(2, result.items().size());
        assertEquals("item1", result.items().get(0));
        assertEquals("item2", result.items().get(1));
        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(2L, result.totalElements());
        assertEquals(1, result.totalPages());

        mutableList.add("item3");
        assertEquals(2, result.items().size());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when items list is null")
    void shouldThrowExceptionWhenItemsIsNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PageResult<>(null, 0, 10, 0L, 0)
        );
        assertEquals("Items list must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when page is negative")
    void shouldThrowExceptionWhenPageIsNegative() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PageResult<>(List.of(), -1, 10, 0L, 0)
        );
        assertEquals("Page index must not be negative", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when size is zero or negative")
    void shouldThrowExceptionWhenSizeIsZeroOrNegative() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PageResult<>(List.of(), 0, 0, 0L, 0)
        );
        assertEquals("Page size must be greater than 0", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when totalElements is negative")
    void shouldThrowExceptionWhenTotalElementsIsNegative() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PageResult<>(List.of(), 0, 10, -1L, 0)
        );
        assertEquals("Total elements must not be negative", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when totalPages is negative")
    void shouldThrowExceptionWhenTotalPagesIsNegative() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PageResult<>(List.of(), 0, 10, 0L, -1)
        );
        assertEquals("Total pages must not be negative", ex.getMessage());
    }
}
