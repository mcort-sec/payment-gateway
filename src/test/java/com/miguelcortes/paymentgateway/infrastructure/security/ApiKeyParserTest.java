package com.miguelcortes.paymentgateway.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyParserTest {

    private ApiKeyParser parser;

    @BeforeEach
    void setUp() {
        parser = new ApiKeyParser();
    }

    @Test
    @DisplayName("Should successfully parse valid Merchant Bearer authorization header")
    void shouldParseValidMerchantBearerAuthorizationHeader() {
        String prefix = "a1B2c3D4e5F6";
        String secret = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"; // 43 chars
        String rawKey = "pg_test_" + prefix + "_" + secret;
        String header = "Bearer " + rawKey;

        Optional<ApiKeyParser.ParsedApiKey> result = parser.parseHeader(header);

        assertTrue(result.isPresent());
        assertInstanceOf(ApiKeyParser.ParsedApiKey.Merchant.class, result.get());
        assertEquals(rawKey, result.get().plaintextKey());
        assertEquals(prefix, result.get().keyPrefix());
    }

    @Test
    @DisplayName("Should successfully parse valid Processor Bearer authorization header")
    void shouldParseValidProcessorBearerAuthorizationHeader() {
        String prefix = "p1R2o3C4e5S6";
        String secret = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"; // 43 chars
        String rawKey = "pg_proc_test_" + prefix + "_" + secret;
        String header = "Bearer " + rawKey;

        Optional<ApiKeyParser.ParsedApiKey> result = parser.parseHeader(header);

        assertTrue(result.isPresent());
        assertInstanceOf(ApiKeyParser.ParsedApiKey.Processor.class, result.get());
        assertEquals(rawKey, result.get().plaintextKey());
        assertEquals(prefix, result.get().keyPrefix());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer ", "bearer ", "BEARER ", "   Bearer ", "Bearer    "})
    @DisplayName("Should accept case-insensitive Bearer prefix and tolerate outer whitespace")
    void shouldAcceptCaseInsensitiveBearerScheme(String prefixHeader) {
        String prefix = "1234567890ab";
        String secret = "1234567890123456789012345678901234567890123";
        String rawKey = "pg_test_" + prefix + "_" + secret;
        String header = prefixHeader.trim() + " " + rawKey;

        Optional<ApiKeyParser.ParsedApiKey> result = parser.parseHeader(header);

        assertTrue(result.isPresent());
        assertInstanceOf(ApiKeyParser.ParsedApiKey.Merchant.class, result.get());
        assertEquals(rawKey, result.get().plaintextKey());
        assertEquals(prefix, result.get().keyPrefix());
    }

    @Test
    @DisplayName("Should return empty when header is null or blank")
    void shouldReturnEmptyWhenHeaderIsNullOrBlank() {
        assertFalse(parser.parseHeader(null).isPresent());
        assertFalse(parser.parseHeader("").isPresent());
        assertFalse(parser.parseHeader("   ").isPresent());
    }

    @Test
    @DisplayName("Should return empty when scheme is not Bearer")
    void shouldReturnEmptyWhenSchemeIsNotBearer() {
        assertFalse(parser.parseHeader("Basic dXNlcjpwYXNz").isPresent());
        assertFalse(parser.parseHeader("Token pg_test_1234567890ab_1234567890123456789012345678901234567890123").isPresent());
    }

    @Test
    @DisplayName("Should return empty when header is just Bearer with no token")
    void shouldReturnEmptyWhenHeaderIsJustBearer() {
        assertFalse(parser.parseHeader("Bearer").isPresent());
        assertFalse(parser.parseHeader("Bearer ").isPresent());
        assertFalse(parser.parseHeader("Bearer    ").isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer pg_test_short_secret",
            "Bearer pg_live_1234567890ab_1234567890123456789012345678901234567890123",
            "Bearer pg_test_1234567890a_1234567890123456789012345678901234567890123",    // prefix 11 chars
            "Bearer pg_test_1234567890abc_1234567890123456789012345678901234567890123",   // prefix 13 chars
            "Bearer pg_test_1234567890ab_123456789012345678901234567890123456789012",     // secret 42 chars
            "Bearer pg_test_1234567890ab_12345678901234567890123456789012345678901234",   // secret 44 chars
            "Bearer pg_test_1234567890ab_123456789012345678901234567890123456789012=",    // invalid char '='
            "Bearer pg_test_1234567890ab_123456789012345678901234567890123456789012+",    // invalid char '+'
            "Bearer pg_proc_test_1234567890a_1234567890123456789012345678901234567890123",  // processor prefix 11 chars
            "Bearer pg_proc_test_1234567890ab_123456789012345678901234567890123456789012",   // processor secret 42 chars
            "Bearer invalid-format"
    })
    @DisplayName("Should return empty when API key format violates pattern")
    void shouldReturnEmptyWhenApiKeyFormatIsInvalid(String invalidHeader) {
        assertFalse(parser.parseHeader(invalidHeader).isPresent());
    }
}
