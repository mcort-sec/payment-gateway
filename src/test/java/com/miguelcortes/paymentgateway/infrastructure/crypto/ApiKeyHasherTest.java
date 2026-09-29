package com.miguelcortes.paymentgateway.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyHasherTest {

    private Sha256ApiKeyHasher hasher;

    private static final Pattern HEX_64_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    @BeforeEach
    void setUp() {
        hasher = new Sha256ApiKeyHasher();
    }

    @Test
    @DisplayName("Should compute deterministic 64-char lowercase hex SHA-256 hash for known vector")
    void shouldComputeDeterministicSha256ForKnownVector() {
        String input = "pg_test_123456789012_abcdefghijklmnopqrstuvwxyz01234567890123456";
        String hash1 = hasher.hash(input);
        String hash2 = hasher.hash(input);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
        assertTrue(HEX_64_PATTERN.matcher(hash1).matches());
        assertEquals(hash1.toLowerCase(), hash1);
    }

    @Test
    @DisplayName("Should produce different hashes for different inputs")
    void shouldProduceDifferentHashesForDifferentInputs() {
        String hash1 = hasher.hash("pg_test_keyA");
        String hash2 = hasher.hash("pg_test_keyB");

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Should verify successfully when plaintext matches expected hash")
    void shouldVerifySuccessfullyWhenMatches() {
        String input = "pg_test_secret_key_123";
        String computedHash = hasher.hash(input);

        assertTrue(hasher.verify(input, computedHash));
    }

    @Test
    @DisplayName("Should fail verification when plaintext does not match expected hash")
    void shouldFailVerificationWhenMismatch() {
        String input = "pg_test_secret_key_123";
        String tamperedInput = "pg_test_secret_key_124";
        String computedHash = hasher.hash(input);

        assertFalse(hasher.verify(tamperedInput, computedHash));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "short", "notAValidHexHashWithInvalidCharactersZZZZZZZZZZZZZZZZZZZZZZZZZZZ"})
    @DisplayName("Should return false safely when expectedHash is null, empty, or malformed")
    void shouldReturnFalseSafelyForMalformedExpectedHash(String malformedHash) {
        assertFalse(hasher.verify("pg_test_key", malformedHash));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should return false safely when plaintext is null or blank in verify")
    void shouldReturnFalseSafelyForBlankPlaintextInVerify(String blankPlaintext) {
        String validHash = hasher.hash("valid_key");
        assertFalse(hasher.verify(blankPlaintext, validHash));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw exception when hashing null or blank input")
    void shouldThrowExceptionWhenHashingNullOrBlank(String invalidInput) {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(invalidInput));
    }
}
