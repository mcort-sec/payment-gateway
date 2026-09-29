package com.miguelcortes.paymentgateway.application.port.out;

public interface ApiKeyHasherPort {

    String hash(String plaintextApiKey);

    boolean verify(String plaintextApiKey, String expectedHash);
}
