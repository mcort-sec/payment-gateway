package com.miguelcortes.paymentgateway.infrastructure.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private static final GrantedAuthority ROLE_MERCHANT = new SimpleGrantedAuthority("ROLE_MERCHANT");
    private static final GrantedAuthority ROLE_PROCESSOR = new SimpleGrantedAuthority("ROLE_PROCESSOR");

    private final Object principal;

    private ApiKeyAuthenticationToken(Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = Objects.requireNonNull(principal, "principal must not be null");
        setAuthenticated(true);
    }

    public static ApiKeyAuthenticationToken authenticatedMerchant(MerchantPrincipal principal) {
        return new ApiKeyAuthenticationToken(principal, List.of(ROLE_MERCHANT));
    }

    public static ApiKeyAuthenticationToken authenticatedProcessor(ProcessorPrincipal principal) {
        return new ApiKeyAuthenticationToken(principal, List.of(ROLE_PROCESSOR));
    }

    public static ApiKeyAuthenticationToken authenticated(MerchantPrincipal principal) {
        return authenticatedMerchant(principal);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
