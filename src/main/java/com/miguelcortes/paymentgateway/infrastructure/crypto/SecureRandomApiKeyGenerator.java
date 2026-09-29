package com.miguelcortes.paymentgateway.infrastructure.crypto;

import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.model.ApiKeyType;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Component
public class SecureRandomApiKeyGenerator implements ApiKeyGeneratorPort {

    private static final String MERCHANT_PREFIX = "pg_test_";
    private static final String PROCESSOR_PREFIX = "pg_proc_test_";
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
    public GeneratedApiKey generate(ApiKeyType type) {
        Objects.requireNonNull(type, "ApiKeyType must not be null");

        String scheme = switch (type) {
            case MERCHANT -> MERCHANT_PREFIX;
            case PROCESSOR -> PROCESSOR_PREFIX;
        };

        String keyPrefix = generateBase62Prefix(PREFIX_LENGTH);
        byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        String fullPlaintextApiKey = scheme + keyPrefix + "_" + secret;

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
