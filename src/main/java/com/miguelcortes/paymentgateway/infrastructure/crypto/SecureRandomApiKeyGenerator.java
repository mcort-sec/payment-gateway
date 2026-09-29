package com.miguelcortes.paymentgateway.infrastructure.crypto;

import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureRandomApiKeyGenerator implements ApiKeyGeneratorPort {

    private static final String ENVIRONMENT_PREFIX = "pg_test_";
    private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int PREFIX_LENGTH = 12;
    private static final int SECRET_BYTES_LENGTH = 32;

    private final SecureRandom secureRandom;

    public SecureRandomApiKeyGenerator() {
        this.secureRandom = new SecureRandom();
    }

    public SecureRandomApiKeyGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public GeneratedApiKey generateTestKey() {
        String keyPrefix = generateBase62Prefix(PREFIX_LENGTH);
        byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        String fullPlaintextApiKey = ENVIRONMENT_PREFIX + keyPrefix + "_" + secret;

        return new GeneratedApiKey(keyPrefix, fullPlaintextApiKey);
    }

    private String generateBase62Prefix(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(BASE62_ALPHABET.length());
            builder.append(BASE62_ALPHABET.charAt(index));
        }
        return builder.toString();
    }
}
