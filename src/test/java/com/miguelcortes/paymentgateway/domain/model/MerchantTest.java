package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidMerchantException;
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

class MerchantTest {

    @Test
    @DisplayName("Should create valid merchant with ACTIVE status by default and trimmed/lowercased values")
    void shouldCreateValidMerchantWithDefaultActiveStatus() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        Merchant merchant = new Merchant(
                id,
                "  Acme Corp  ",
                "  CONTACT@AcmeCorp.COM  ",
                createdAt
        );

        assertEquals(id, merchant.getId());
        assertEquals("Acme Corp", merchant.getName());
        assertEquals("contact@acmecorp.com", merchant.getEmail());
        assertEquals(MerchantStatus.ACTIVE, merchant.getStatus());
        assertTrue(merchant.isActive());
        assertEquals(createdAt, merchant.getCreatedAt());
    }

    @Test
    @DisplayName("Should reconstitute existing merchant with specified status")
    void shouldReconstituteMerchantWithSpecifiedStatus() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T10:00:00Z");

        Merchant merchant = Merchant.reconstitute(
                id,
                "Beta Store",
                "beta@store.com",
                MerchantStatus.SUSPENDED,
                createdAt
        );

        assertEquals(id, merchant.getId());
        assertEquals("Beta Store", merchant.getName());
        assertEquals("beta@store.com", merchant.getEmail());
        assertEquals(MerchantStatus.SUSPENDED, merchant.getStatus());
        assertFalse(merchant.isActive());
        assertEquals(createdAt, merchant.getCreatedAt());
    }

    @Test
    @DisplayName("Should suspend and reactivate merchant")
    void shouldSuspendAndReactivateMerchant() {
        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                "Store",
                "store@test.com",
                Instant.now()
        );

        assertTrue(merchant.isActive());

        merchant.suspend();
        assertEquals(MerchantStatus.SUSPENDED, merchant.getStatus());
        assertFalse(merchant.isActive());

        merchant.activate();
        assertEquals(MerchantStatus.ACTIVE, merchant.getStatus());
        assertTrue(merchant.isActive());
    }

    @Test
    @DisplayName("Should throw exception when ID is null")
    void shouldThrowExceptionWhenIdIsNull() {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(null, "Name", "email@test.com", Instant.now())
        );
        assertEquals("Merchant ID cannot be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should throw exception when name is empty or blank")
    void shouldThrowExceptionWhenNameIsEmptyOrBlank(String blankName) {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(UUID.randomUUID(), blankName, "email@test.com", Instant.now())
        );
        assertEquals("Merchant name cannot be null, empty, or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void shouldThrowExceptionWhenNameIsNull() {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(UUID.randomUUID(), null, "email@test.com", Instant.now())
        );
        assertEquals("Merchant name cannot be null, empty, or blank", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should throw exception when email is empty or blank")
    void shouldThrowExceptionWhenEmailIsEmptyOrBlank(String blankEmail) {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(UUID.randomUUID(), "Name", blankEmail, Instant.now())
        );
        assertEquals("Merchant email cannot be null, empty, or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when email is null")
    void shouldThrowExceptionWhenEmailIsNull() {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(UUID.randomUUID(), "Name", null, Instant.now())
        );
        assertEquals("Merchant email cannot be null, empty, or blank", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainaddress", "missingatsign.com", "@missingusername.com", "user@.com", "user@domain"})
    @DisplayName("Should throw exception when email format is invalid")
    void shouldThrowExceptionWhenEmailFormatIsInvalid(String invalidEmail) {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(UUID.randomUUID(), "Name", invalidEmail, Instant.now())
        );
        assertTrue(exception.getMessage().contains("Merchant email format is invalid"));
    }

    @Test
    @DisplayName("Should throw exception when status is null in full constructor")
    void shouldThrowExceptionWhenStatusIsNull() {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(UUID.randomUUID(), "Name", "email@test.com", null, Instant.now())
        );
        assertEquals("Merchant status cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when createdAt is null")
    void shouldThrowExceptionWhenCreatedAtIsNull() {
        InvalidMerchantException exception = assertThrows(
                InvalidMerchantException.class,
                () -> new Merchant(UUID.randomUUID(), "Name", "email@test.com", null)
        );
        assertEquals("Creation timestamp cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should evaluate equality and hashCode based on ID")
    void shouldEvaluateEqualityAndHashCodeById() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Merchant m1 = new Merchant(id1, "Store 1", "s1@test.com", Instant.now());
        Merchant m2 = new Merchant(id1, "Store 1 Updated", "s1-up@test.com", Instant.now());
        Merchant m3 = new Merchant(id2, "Store 2", "s2@test.com", Instant.now());

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotEquals(m1, m3);
    }
}
