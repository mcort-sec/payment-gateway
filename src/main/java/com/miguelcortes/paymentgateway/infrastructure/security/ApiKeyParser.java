package com.miguelcortes.paymentgateway.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiKeyParser {

    private static final String BEARER_PREFIX = "bearer ";
    private static final Pattern MERCHANT_KEY_PATTERN = Pattern.compile("^pg_test_([a-zA-Z0-9]{12})_[a-zA-Z0-9_-]{43}$");
    private static final Pattern PROCESSOR_KEY_PATTERN = Pattern.compile("^pg_proc_test_([a-zA-Z0-9]{12})_[a-zA-Z0-9_-]{43}$");

    public Optional<ParsedApiKey> parseHeader(String authorizationHeader) {
        if (authorizationHeader == null) {
            return Optional.empty();
        }

        String trimmedHeader = authorizationHeader.trim();
        if (trimmedHeader.length() < 7 || !trimmedHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }

        String rawKey = trimmedHeader.substring(BEARER_PREFIX.length()).trim();

        Matcher processorMatcher = PROCESSOR_KEY_PATTERN.matcher(rawKey);
        if (processorMatcher.matches()) {
            return Optional.of(new ParsedApiKey.Processor(rawKey, processorMatcher.group(1)));
        }

        Matcher merchantMatcher = MERCHANT_KEY_PATTERN.matcher(rawKey);
        if (merchantMatcher.matches()) {
            return Optional.of(new ParsedApiKey.Merchant(rawKey, merchantMatcher.group(1)));
        }

        return Optional.empty();
    }

    public sealed interface ParsedApiKey permits ParsedApiKey.Merchant, ParsedApiKey.Processor {
        String plaintextKey();
        String keyPrefix();

        record Merchant(String plaintextKey, String keyPrefix) implements ParsedApiKey {}
        record Processor(String plaintextKey, String keyPrefix) implements ParsedApiKey {}
    }
}
