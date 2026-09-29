package com.miguelcortes.paymentgateway.infrastructure.security;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
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
import org.springframework.security.core.GrantedAuthority;
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
    private ProcessorCredentialRepositoryPort processorCredentialRepository;

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
                processorCredentialRepository,
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
    @DisplayName("Should reject and commence 401 when Merchant API key prefix is not found in repository")
    void shouldRejectWhenMerchantPrefixNotFound() throws ServletException, IOException {
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
    @DisplayName("Should reject and commence 401 when Merchant API credential is REVOKED")
    void shouldRejectWhenMerchantCredentialIsRevoked() throws ServletException, IOException {
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
    @DisplayName("Should reject and commence 401 when Merchant API key hash verification fails")
    void shouldRejectWhenMerchantHashVerificationFails() throws ServletException, IOException {
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
    @DisplayName("Should authenticate and set MerchantPrincipal with ROLE_MERCHANT in SecurityContext when key is valid")
    void shouldAuthenticateMerchantSuccessfullyWhenKeyIsValid() throws ServletException, IOException {
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
        assertEquals(1, authentication.getAuthorities().size());
        assertTrue(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_MERCHANT")));

        MerchantPrincipal principal = (MerchantPrincipal) authentication.getPrincipal();
        assertEquals(merchantId, principal.merchantId());
    }

    @Test
    @DisplayName("Should authenticate and set ProcessorPrincipal with ROLE_PROCESSOR in SecurityContext when key is valid")
    void shouldAuthenticateProcessorSuccessfullyWhenKeyIsValid() throws ServletException, IOException {
        String prefix = "procpref0001";
        String rawKey = "pg_proc_test_" + prefix + "_1234567890123456789012345678901234567890123";
        String expectedHash = "d".repeat(64);
        UUID processorId = UUID.randomUUID();

        ProcessorCredential credential = new ProcessorCredential(
                UUID.randomUUID(),
                processorId,
                prefix,
                expectedHash,
                Instant.now()
        );

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(processorCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.of(credential));
        when(apiKeyHasher.verify(rawKey, expectedHash)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication instanceof ApiKeyAuthenticationToken);
        assertTrue(authentication.isAuthenticated());
        assertNull(authentication.getCredentials());
        assertEquals(1, authentication.getAuthorities().size());
        assertTrue(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_PROCESSOR")));

        ProcessorPrincipal principal = (ProcessorPrincipal) authentication.getPrincipal();
        assertEquals(processorId, principal.processorId());
    }

    @Test
    @DisplayName("Should reject and commence 401 when Processor API key prefix is not found in repository")
    void shouldRejectWhenProcessorPrefixNotFound() throws ServletException, IOException {
        String prefix = "procpref0002";
        String rawKey = "pg_proc_test_" + prefix + "_1234567890123456789012345678901234567890123";
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(processorCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject and commence 401 when Processor API credential is REVOKED")
    void shouldRejectWhenProcessorCredentialIsRevoked() throws ServletException, IOException {
        String prefix = "procpref0003";
        String rawKey = "pg_proc_test_" + prefix + "_1234567890123456789012345678901234567890123";
        ProcessorCredential revokedCredential = ProcessorCredential.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                prefix,
                "e".repeat(64),
                CredentialStatus.REVOKED,
                Instant.now(),
                Instant.now()
        );

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(processorCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.of(revokedCredential));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should reject and commence 401 when Processor API key hash verification fails")
    void shouldRejectWhenProcessorHashVerificationFails() throws ServletException, IOException {
        String prefix = "procpref0004";
        String rawKey = "pg_proc_test_" + prefix + "_1234567890123456789012345678901234567890123";
        String expectedHash = "f".repeat(64);
        ProcessorCredential credential = new ProcessorCredential(
                UUID.randomUUID(),
                UUID.randomUUID(),
                prefix,
                expectedHash,
                Instant.now()
        );

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + rawKey);
        when(processorCredentialRepository.findByKeyPrefix(prefix)).thenReturn(Optional.of(credential));
        when(apiKeyHasher.verify(rawKey, expectedHash)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(apiKeyHasher).verify(rawKey, expectedHash);
        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
