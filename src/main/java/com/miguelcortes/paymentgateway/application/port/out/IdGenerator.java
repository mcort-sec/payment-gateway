package com.miguelcortes.paymentgateway.application.port.out;

import java.util.UUID;

public interface IdGenerator {
    UUID generate();
}
