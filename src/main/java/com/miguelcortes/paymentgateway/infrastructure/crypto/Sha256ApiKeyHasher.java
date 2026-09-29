package com.miguelcortes.paymentgateway.infrastructure.crypto;

import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Sha256ApiKeyHasher implements ApiKeyHasherPort {

    private static final String ALGORITHM = "SHA-256";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    @Override
    public String hash(String plaintextApiKey) {
        if (plaintextApiKey == null || plaintextApiKey.isBlank()) {
            throw new IllegalArgumentException("Plaintext API key cannot be null or blank");
        }
        byte[] hashBytes = computeDigest(plaintextApiKey);
        return HEX_FORMAT.formatHex(hashBytes);
    }

    @Override
    public boolean verify(String plaintextApiKey, String expectedHash) {
        if (plaintextApiKey == null || plaintextApiKey.isBlank() || expectedHash == null || expectedHash.length() != 64) {
            return false;
        }

        byte[] expectedBytes;
        try {
            expectedBytes = HEX_FORMAT.parseHex(expectedHash);
        } catch (IllegalArgumentException ex) {
            // Malformed expected hex hash
            return false;
        }

        byte[] computedBytes = computeDigest(plaintextApiKey);
        return MessageDigest.isEqual(computedBytes, expectedBytes);
    }

    private byte[] computeDigest(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
