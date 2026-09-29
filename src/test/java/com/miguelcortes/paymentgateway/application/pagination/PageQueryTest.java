package com.miguelcortes.paymentgateway.application.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageQueryTest {

    @Test
    @DisplayName("Should create PageQuery with valid default parameters")
    void shouldCreatePageQueryWithValidDefaultParameters() {
        PageQuery query = new PageQuery(0, 20);
        assertEquals(0, query.page());
        assertEquals(20, query.size());
    }

    @Test
    @DisplayName("Should create PageQuery with page > 0")
    void shouldCreatePageQueryWithPositivePage() {
        PageQuery query = new PageQuery(5, 50);
        assertEquals(5, query.page());
        assertEquals(50, query.size());
    }

    @Test
    @DisplayName("Should create PageQuery with explicit positive page (3, 20)")
    void shouldCreatePageQueryWithExplicitPositivePage() {
        PageQuery query = new PageQuery(3, 20);
        assertEquals(3, query.page());
        assertEquals(20, query.size());
    }

    @Test
    @DisplayName("Should create PageQuery with min size 1")
    void shouldCreatePageQueryWithMinSizeOne() {
        PageQuery query = new PageQuery(0, 1);
        assertEquals(0, query.page());
        assertEquals(1, query.size());
    }

    @Test
    @DisplayName("Should create PageQuery with boundary size 1 and 100")
    void shouldCreatePageQueryWithBoundarySizes() {
        assertDoesNotThrow(() -> new PageQuery(0, 1));
        assertDoesNotThrow(() -> new PageQuery(0, 100));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when page is negative")
    void shouldThrowExceptionWhenPageIsNegative() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PageQuery(-1, 20)
        );
        assertEquals("Page index must not be negative", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when size is zero or negative")
    void shouldThrowExceptionWhenSizeIsZeroOrNegative() {
        IllegalArgumentException exZero = assertThrows(
                IllegalArgumentException.class,
                () -> new PageQuery(0, 0)
        );
        assertEquals("Page size must be between 1 and 100", exZero.getMessage());

        IllegalArgumentException exNegative = assertThrows(
                IllegalArgumentException.class,
                () -> new PageQuery(0, -5)
        );
        assertEquals("Page size must be between 1 and 100", exNegative.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when size exceeds MAX_SIZE (100)")
    void shouldThrowExceptionWhenSizeExceedsMax() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PageQuery(0, 101)
        );
        assertEquals("Page size must be between 1 and 100", ex.getMessage());
    }
}
