package com.miguelcortes.paymentgateway.application.command;

public record CreateMerchantCommand(
        String name,
        String email
) {
}
