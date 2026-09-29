package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidProcessorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorTest {

    @Test
    @DisplayName("Should create valid processor with ACTIVE status by default and trimmed name")
    void shouldCreateValidProcessorWithDefaultActiveStatus() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        Processor processor = new Processor(
                id,
                "  Visa Processor  ",
                createdAt
        );

        assertEquals(id, processor.getId());
        assertEquals("Visa Processor", processor.getName());
        assertEquals(ProcessorStatus.ACTIVE, processor.getStatus());
        assertTrue(processor.isActive());
        assertFalse(processor.isSuspended());
        assertEquals(createdAt, processor.getCreatedAt());
    }

    @Test
    @DisplayName("Should reconstitute existing processor with specified status")
    void shouldReconstituteProcessorWithSpecifiedStatus() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T10:00:00Z");

        Processor processor = Processor.reconstitute(
                id,
                "MasterCard Gateway",
                ProcessorStatus.SUSPENDED,
                createdAt
        );

        assertEquals(id, processor.getId());
        assertEquals("MasterCard Gateway", processor.getName());
        assertEquals(ProcessorStatus.SUSPENDED, processor.getStatus());
        assertFalse(processor.isActive());
        assertTrue(processor.isSuspended());
        assertEquals(createdAt, processor.getCreatedAt());
    }

    @Test
    @DisplayName("Should suspend and reactivate processor")
    void shouldSuspendAndReactivateProcessor() {
        Processor processor = new Processor(
                UUID.randomUUID(),
                "Processor X",
                Instant.now()
        );

        assertTrue(processor.isActive());

        processor.suspend();
        assertEquals(ProcessorStatus.SUSPENDED, processor.getStatus());
        assertFalse(processor.isActive());
        assertTrue(processor.isSuspended());

        processor.activate();
        assertEquals(ProcessorStatus.ACTIVE, processor.getStatus());
        assertTrue(processor.isActive());
        assertFalse(processor.isSuspended());
    }

    @Test
    @DisplayName("Should throw exception when ID is null")
    void shouldThrowExceptionWhenIdIsNull() {
        InvalidProcessorException exception = assertThrows(
                InvalidProcessorException.class,
                () -> new Processor(null, "Name", Instant.now())
        );
        assertEquals("Processor ID cannot be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should throw exception when name is empty or blank")
    void shouldThrowExceptionWhenNameIsEmptyOrBlank(String blankName) {
        InvalidProcessorException exception = assertThrows(
                InvalidProcessorException.class,
                () -> new Processor(UUID.randomUUID(), blankName, Instant.now())
        );
        assertEquals("Processor name cannot be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void shouldThrowExceptionWhenNameIsNull() {
        InvalidProcessorException exception = assertThrows(
                InvalidProcessorException.class,
                () -> new Processor(UUID.randomUUID(), null, Instant.now())
        );
        assertEquals("Processor name cannot be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when name exceeds 100 characters")
    void shouldThrowExceptionWhenNameExceeds100Characters() {
        String longName = "A".repeat(101);
        InvalidProcessorException exception = assertThrows(
                InvalidProcessorException.class,
                () -> new Processor(UUID.randomUUID(), longName, Instant.now())
        );
        assertEquals("Processor name cannot exceed 100 characters", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when status is null in full constructor")
    void shouldThrowExceptionWhenStatusIsNull() {
        InvalidProcessorException exception = assertThrows(
                InvalidProcessorException.class,
                () -> new Processor(UUID.randomUUID(), "Name", null, Instant.now())
        );
        assertEquals("Processor status cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when createdAt is null")
    void shouldThrowExceptionWhenCreatedAtIsNull() {
        InvalidProcessorException exception = assertThrows(
                InvalidProcessorException.class,
                () -> new Processor(UUID.randomUUID(), "Name", null)
        );
        assertEquals("CreatedAt timestamp cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should evaluate equality and hashCode based on ID")
    void shouldEvaluateEqualityAndHashCodeById() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Processor p1 = new Processor(id1, "Proc 1", Instant.now());
        Processor p2 = new Processor(id1, "Proc 1 Updated", Instant.now());
        Processor p3 = new Processor(id2, "Proc 2", Instant.now());

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
    }
}
