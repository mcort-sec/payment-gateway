package com.miguelcortes.paymentgateway.infrastructure.security;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiCredentialRepositoryPort apiCredentialRepository;

    @Mock
    private ApiKeyHasherPort apiKeyHasher;

    @Mock
    private ApiKeyAuthenticationEntryPoint authenticationEntryPoint;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private ApiKeyParser apiKeyParser;
    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        apiKeyParser = new ApiKeyParser();
        filter = new ApiKeyAuthenticationFilter(
                apiKeyParser,
                apiCredentialRepository,
                apiKeyHasher,
                authenticationEntryPoint
        );
    }

    @Test
    @DisplayName("Should pass through without authentication when Authorization header is missing")
    void shouldPassThroughWhenAuthorizationHeaderIsMissing() throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should pass through without authentication when Authorization header is blank")
    void shouldPassThroughWhenAuthorizationHeaderIsBlank() throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject and commence 401 when Authorization header format is invalid")
    void shouldRejectWhenAuthorizationHeaderFormatIsInvalid() throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer invalid_token_format");

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject and commence 401 when API key prefix is not found in repository")
    void shouldRejectWhenPrefixNotFound() throws ServletException, IOException {
        String prefix = "abcdef123456";
        String rawKey = "pg_test_" + prefix + "_1234567890123456789012345678901234567890123";
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(apiCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject and commence 401 when API credential is REVOKED")
    void shouldRejectWhenCredentialIsRevoked() throws ServletException, IOException {
        String prefix = "abcdef123456";
        String rawKey = "pg_test_" + prefix + "_1234567890123456789012345678901234567890123";
        ApiCredential revokedCredential = ApiCredential.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                prefix,
                "a".repeat(64),
                CredentialStatus.REVOKED,
                Instant.now(),
                Instant.now()
        );

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(apiCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.of(revokedCredential));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject and commence 401 when API key hash verification fails")
    void shouldRejectWhenHashVerificationFails() throws ServletException, IOException {
        String prefix = "abcdef123456";
        String rawKey = "pg_test_" + prefix + "_1234567890123456789012345678901234567890123";
        String expectedHash = "b".repeat(64);
        ApiCredential credential = new ApiCredential(
                UUID.randomUUID(),
                UUID.randomUUID(),
                prefix,
                expectedHash,
                Instant.now()
        );

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(apiCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.of(credential));
        when(apiKeyHasher.verify(rawKey, expectedHash)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should authenticate and set MerchantPrincipal in SecurityContext when API key is valid")
    void shouldAuthenticateSuccessfullyWhenKeyIsValid() throws ServletException, IOException {
        String prefix = "abcdef123456";
        String rawKey = "pg_test_" + prefix + "_1234567890123456789012345678901234567890123";
        String expectedHash = "c".repeat(64);
        UUID merchantId = UUID.randomUUID();

        ApiCredential credential = new ApiCredential(
                UUID.randomUUID(),
                merchantId,
                prefix,
                expectedHash,
                Instant.now()
        );

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(apiCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.of(credential));
        when(apiKeyHasher.verify(rawKey, expectedHash)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication instanceof ApiKeyAuthenticationToken);
        assertTrue(authentication.isAuthenticated());
        assertNull(authentication.getCredentials());
        assertTrue(authentication.getAuthorities().isEmpty());

        MerchantPrincipal principal = (MerchantPrincipal) authentication.getPrincipal();
        assertEquals(merchantId, principal.merchantId());
    }
}
