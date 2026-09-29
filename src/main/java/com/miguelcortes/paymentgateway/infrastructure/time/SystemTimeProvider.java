package com.miguelcortes.paymentgateway.infrastructure.time;

import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SystemTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
