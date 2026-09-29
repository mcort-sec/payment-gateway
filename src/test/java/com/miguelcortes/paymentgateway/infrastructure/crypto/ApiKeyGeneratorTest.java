package com.miguelcortes.paymentgateway.infrastructure.crypto;

import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyGeneratorTest {

    private SecureRandomApiKeyGenerator generator;

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[a-zA-Z0-9]{12}$");
    private static final Pattern FULL_KEY_PATTERN = Pattern.compile("^pg_test_[a-zA-Z0-9]{12}_[a-zA-Z0-9_-]{43}$");

    @BeforeEach
    void setUp() {
        generator = new SecureRandomApiKeyGenerator();
    }

    @Test
    @DisplayName("Should generate valid API key with exact structure and 64 total characters")
    void shouldGenerateValidApiKeyWithExactStructure() {
        GeneratedApiKey generated = generator.generateTestKey();

        assertNotNull(generated);
        assertNotNull(generated.keyPrefix());
        assertNotNull(generated.fullPlaintextApiKey());

        assertEquals(12, generated.keyPrefix().length());
        assertTrue(PREFIX_PATTERN.matcher(generated.keyPrefix()).matches());

        assertEquals(64, generated.fullPlaintextApiKey().length());
        assertTrue(FULL_KEY_PATTERN.matcher(generated.fullPlaintextApiKey()).matches());
        assertTrue(generated.fullPlaintextApiKey().startsWith("pg_test_" + generated.keyPrefix() + "_"));
    }

    @Test
    @DisplayName("Should decode secret portion to exactly 32 bytes from Base64URL")
    void shouldDecodeSecretToExactly32Bytes() {
        GeneratedApiKey generated = generator.generateTestKey();
        String fullKey = generated.fullPlaintextApiKey();

        // Extract secret part after pg_test_<prefix>_
        String secretPart = fullKey.substring("pg_test_".length() + 12 + 1);
        assertEquals(43, secretPart.length());

        byte[] decodedBytes = Base64.getUrlDecoder().decode(secretPart);
        assertEquals(32, decodedBytes.length, "Secret must represent exactly 32 cryptographically secure bytes");
    }

    @Test
    @DisplayName("Should not contain +, /, or = characters in generated API key")
    void shouldNotContainForbiddenBase64Characters() {
        for (int i = 0; i < 50; i++) {
            GeneratedApiKey generated = generator.generateTestKey();
            String fullKey = generated.fullPlaintextApiKey();

            assertFalse(fullKey.contains("+"), "Must not contain +");
            assertFalse(fullKey.contains("/"), "Must not contain /");
            assertFalse(fullKey.contains("="), "Must not contain padding =");
        }
    }

    @Test
    @DisplayName("Should generate distinct keys and prefixes on consecutive calls")
    void shouldGenerateDistinctKeysOnConsecutiveCalls() {
        GeneratedApiKey key1 = generator.generateTestKey();
        GeneratedApiKey key2 = generator.generateTestKey();

        assertNotEquals(key1.keyPrefix(), key2.keyPrefix());
        assertNotEquals(key1.fullPlaintextApiKey(), key2.fullPlaintextApiKey());
    }
}
