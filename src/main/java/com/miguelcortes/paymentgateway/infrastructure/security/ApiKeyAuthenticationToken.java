package com.miguelcortes.paymentgateway.infrastructure.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final MerchantPrincipal principal;

    private ApiKeyAuthenticationToken(MerchantPrincipal principal) {
        super(Collections.emptyList());
        this.principal = principal;
        setAuthenticated(true);
    }

    public static ApiKeyAuthenticationToken authenticated(MerchantPrincipal principal) {
        return new ApiKeyAuthenticationToken(principal);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public MerchantPrincipal getPrincipal() {
        return principal;
    }
}
