package com.miguelcortes.paymentgateway.infrastructure.security;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyParser apiKeyParser;
    private final ApiCredentialRepositoryPort apiCredentialRepository;
    private final ProcessorCredentialRepositoryPort processorCredentialRepository;
    private final ApiKeyHasherPort apiKeyHasher;
    private final ApiKeyAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            ApiKeyParser apiKeyParser,
            ApiCredentialRepositoryPort apiCredentialRepository,
            ProcessorCredentialRepositoryPort processorCredentialRepository,
            ApiKeyHasherPort apiKeyHasher,
            ApiKeyAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) {
        this.apiKeyParser = apiKeyParser;
        this.apiCredentialRepository = apiCredentialRepository;
        this.processorCredentialRepository = processorCredentialRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(
                apiKeyParser,
                apiCredentialRepository,
                processorCredentialRepository,
                apiKeyHasher,
                authenticationEntryPoint
        );

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/payments").hasAuthority("ROLE_MERCHANT")
                        .requestMatchers(HttpMethod.GET, "/payments", "/payments/*").hasAuthority("ROLE_MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/payments/*/cancel").hasAuthority("ROLE_MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/payments/*/refunds").hasAuthority("ROLE_MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/payments/*/approve").hasAuthority("ROLE_PROCESSOR")
                        .requestMatchers(HttpMethod.POST, "/payments/*/decline").hasAuthority("ROLE_PROCESSOR")
                        .requestMatchers(HttpMethod.GET, "/refunds", "/refunds/*").hasAuthority("ROLE_MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/refunds/*/approve").hasAuthority("ROLE_PROCESSOR")
                        .requestMatchers(HttpMethod.POST, "/refunds/*/decline").hasAuthority("ROLE_PROCESSOR")
                        .anyRequest().denyAll()
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
