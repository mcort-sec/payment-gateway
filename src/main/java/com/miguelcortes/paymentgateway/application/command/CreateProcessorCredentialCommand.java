package com.miguelcortes.paymentgateway.application.command;

import java.util.UUID;

public record CreateProcessorCredentialCommand(
        UUID processorId
) {}
