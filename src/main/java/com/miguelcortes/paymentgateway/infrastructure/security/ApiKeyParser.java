package com.miguelcortes.paymentgateway.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiKeyParser {

    private static final String BEARER_PREFIX = "bearer ";
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^pg_test_([a-zA-Z0-9]{12})_[a-zA-Z0-9_-]{43}$");

    public Optional<ParsedApiKey> parseHeader(String authorizationHeader) {
        if (authorizationHeader == null) {
            return Optional.empty();
        }

        String trimmedHeader = authorizationHeader.trim();
        if (trimmedHeader.length() < 7 || !trimmedHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }

        String rawKey = trimmedHeader.substring(BEARER_PREFIX.length()).trim();
        Matcher matcher = API_KEY_PATTERN.matcher(rawKey);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String prefix = matcher.group(1);
        return Optional.of(new ParsedApiKey(rawKey, prefix));
    }

    public record ParsedApiKey(String plaintextKey, String keyPrefix) {
    }
}
