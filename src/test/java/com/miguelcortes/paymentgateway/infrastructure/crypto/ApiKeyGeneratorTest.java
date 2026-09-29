package com.miguelcortes.paymentgateway.infrastructure.crypto;

import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.model.ApiKeyType;
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
    private static final Pattern MERCHANT_KEY_PATTERN = Pattern.compile("^pg_test_[a-zA-Z0-9]{12}_[a-zA-Z0-9_-]{43}$");
    private static final Pattern PROCESSOR_KEY_PATTERN = Pattern.compile("^pg_proc_test_[a-zA-Z0-9]{12}_[a-zA-Z0-9_-]{43}$");

    @BeforeEach
    void setUp() {
        generator = new SecureRandomApiKeyGenerator();
    }

    @Test
    @DisplayName("Should generate valid Merchant API key with exact structure and 64 total characters")
    void shouldGenerateValidMerchantApiKeyWithExactStructure() {
        GeneratedApiKey generated = generator.generate(ApiKeyType.MERCHANT);

        assertNotNull(generated);
        assertNotNull(generated.keyPrefix());
        assertNotNull(generated.plaintextApiKey());

        assertEquals(12, generated.keyPrefix().length());
        assertTrue(PREFIX_PATTERN.matcher(generated.keyPrefix()).matches());

        assertEquals(64, generated.plaintextApiKey().length());
        assertTrue(MERCHANT_KEY_PATTERN.matcher(generated.plaintextApiKey()).matches());
        assertTrue(generated.plaintextApiKey().startsWith("pg_test_" + generated.keyPrefix() + "_"));
    }

    @Test
    @DisplayName("Should generate valid Processor API key with exact structure and 69 total characters")
    void shouldGenerateValidProcessorApiKeyWithExactStructure() {
        GeneratedApiKey generated = generator.generate(ApiKeyType.PROCESSOR);

        assertNotNull(generated);
        assertNotNull(generated.keyPrefix());
        assertNotNull(generated.plaintextApiKey());

        assertEquals(12, generated.keyPrefix().length());
        assertTrue(PREFIX_PATTERN.matcher(generated.keyPrefix()).matches());

        assertEquals(69, generated.plaintextApiKey().length());
        assertTrue(PROCESSOR_KEY_PATTERN.matcher(generated.plaintextApiKey()).matches());
        assertTrue(generated.plaintextApiKey().startsWith("pg_proc_test_" + generated.keyPrefix() + "_"));
    }

    @Test
    @DisplayName("Should decode secret portion to exactly 32 bytes from Base64URL for both Merchant and Processor")
    void shouldDecodeSecretToExactly32Bytes() {
        GeneratedApiKey merchantKey = generator.generate(ApiKeyType.MERCHANT);
        String merchantSecret = merchantKey.plaintextApiKey().substring("pg_test_".length() + 12 + 1);
        assertEquals(43, merchantSecret.length());
        byte[] merchantBytes = Base64.getUrlDecoder().decode(merchantSecret);
        assertEquals(32, merchantBytes.length, "Merchant secret must represent exactly 32 cryptographically secure bytes");

        GeneratedApiKey processorKey = generator.generate(ApiKeyType.PROCESSOR);
        String processorSecret = processorKey.plaintextApiKey().substring("pg_proc_test_".length() + 12 + 1);
        assertEquals(43, processorSecret.length());
        byte[] processorBytes = Base64.getUrlDecoder().decode(processorSecret);
        assertEquals(32, processorBytes.length, "Processor secret must represent exactly 32 cryptographically secure bytes");
    }

    @Test
    @DisplayName("Should not contain +, /, or = characters in generated API keys")
    void shouldNotContainForbiddenBase64Characters() {
        for (int i = 0; i < 25; i++) {
            GeneratedApiKey merch = generator.generate(ApiKeyType.MERCHANT);
            assertFalse(merch.plaintextApiKey().contains("+"));
            assertFalse(merch.plaintextApiKey().contains("/"));
            assertFalse(merch.plaintextApiKey().contains("="));

            GeneratedApiKey proc = generator.generate(ApiKeyType.PROCESSOR);
            assertFalse(proc.plaintextApiKey().contains("+"));
            assertFalse(proc.plaintextApiKey().contains("/"));
            assertFalse(proc.plaintextApiKey().contains("="));
        }
    }

    @Test
    @DisplayName("Should generate distinct keys and prefixes on consecutive calls")
    void shouldGenerateDistinctKeysOnConsecutiveCalls() {
        GeneratedApiKey key1 = generator.generate(ApiKeyType.MERCHANT);
        GeneratedApiKey key2 = generator.generate(ApiKeyType.PROCESSOR);

        assertNotEquals(key1.keyPrefix(), key2.keyPrefix());
        assertNotEquals(key1.plaintextApiKey(), key2.plaintextApiKey());
    }
}
