package com.miguelcortes.paymentgateway.application.command;

import java.util.UUID;

public record CreateApiCredentialCommand(
        UUID merchantId
) {}
