package com.miguelcortes.paymentgateway.application.exception;

import java.util.UUID;

public class ProcessorNotFoundException extends RuntimeException {
    public ProcessorNotFoundException(UUID id) {
        super("Processor not found with id: " + id);
    }
}
