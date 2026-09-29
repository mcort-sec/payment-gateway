package com.miguelcortes.paymentgateway.infrastructure.generator;

import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidGenerator implements IdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
