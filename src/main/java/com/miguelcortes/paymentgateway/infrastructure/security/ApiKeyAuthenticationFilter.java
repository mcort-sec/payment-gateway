package com.miguelcortes.paymentgateway.infrastructure.security;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyParser apiKeyParser;
    private final ApiCredentialRepositoryPort apiCredentialRepository;
    private final ProcessorCredentialRepositoryPort processorCredentialRepository;
    private final ApiKeyHasherPort apiKeyHasher;
    private final ApiKeyAuthenticationEntryPoint authenticationEntryPoint;

    public ApiKeyAuthenticationFilter(
            ApiKeyParser apiKeyParser,
            ApiCredentialRepositoryPort apiCredentialRepository,
            ProcessorCredentialRepositoryPort processorCredentialRepository,
            ApiKeyHasherPort apiKeyHasher,
            ApiKeyAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.apiKeyParser = apiKeyParser;
        this.apiCredentialRepository = apiCredentialRepository;
        this.processorCredentialRepository = processorCredentialRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKeyParser.ParsedApiKey> parsedKeyOpt = apiKeyParser.parseHeader(authHeader);
        if (parsedKeyOpt.isEmpty()) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("Invalid API key format")
            );
            return;
        }

        ApiKeyParser.ParsedApiKey parsedKey = parsedKeyOpt.get();

        if (parsedKey instanceof ApiKeyParser.ParsedApiKey.Merchant merchantKey) {
            Optional<ApiCredential> credentialOpt = apiCredentialRepository.findByKeyPrefix(merchantKey.keyPrefix());

            if (credentialOpt.isEmpty()) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("API credential not found")
                );
                return;
            }

            ApiCredential credential = credentialOpt.get();
            if (!credential.isActive() || !apiKeyHasher.verify(merchantKey.plaintextKey(), credential.getKeyHash())) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("API key verification failed")
                );
                return;
            }

            MerchantPrincipal principal = new MerchantPrincipal(credential.getMerchantId());
            ApiKeyAuthenticationToken authenticationToken = ApiKeyAuthenticationToken.authenticatedMerchant(principal);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticationToken);
            SecurityContextHolder.setContext(context);

            filterChain.doFilter(request, response);
            return;
        }

        if (parsedKey instanceof ApiKeyParser.ParsedApiKey.Processor processorKey) {
            Optional<ProcessorCredential> credentialOpt = processorCredentialRepository.findByKeyPrefix(processorKey.keyPrefix());

            if (credentialOpt.isEmpty()) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("API credential not found")
                );
                return;
            }

            ProcessorCredential credential = credentialOpt.get();
            if (!credential.isActive() || !apiKeyHasher.verify(processorKey.plaintextKey(), credential.getKeyHash())) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("API key verification failed")
                );
                return;
            }

            ProcessorPrincipal principal = new ProcessorPrincipal(credential.getProcessorId());
            ApiKeyAuthenticationToken authenticationToken = ApiKeyAuthenticationToken.authenticatedProcessor(principal);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticationToken);
            SecurityContextHolder.setContext(context);

            filterChain.doFilter(request, response);
            return;
        }

        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Unsupported API key type")
        );
    }
}
