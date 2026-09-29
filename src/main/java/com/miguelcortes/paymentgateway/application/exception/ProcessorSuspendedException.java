package com.miguelcortes.paymentgateway.application.exception;

import java.util.UUID;

public class ProcessorSuspendedException extends RuntimeException {
    public ProcessorSuspendedException(UUID id) {
        super("Processor is suspended: " + id);
    }
}
