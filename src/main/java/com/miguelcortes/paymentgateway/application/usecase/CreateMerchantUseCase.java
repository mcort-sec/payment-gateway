package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateMerchantCommand;
import com.miguelcortes.paymentgateway.application.exception.DuplicateMerchantEmailException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Merchant;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public class CreateMerchantUseCase {

    private final MerchantRepositoryPort merchantRepositoryPort;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public CreateMerchantUseCase(
            MerchantRepositoryPort merchantRepositoryPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        this.merchantRepositoryPort = merchantRepositoryPort;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public Merchant execute(CreateMerchantCommand command) {
        String normalizedEmail = command.email() != null
                ? command.email().trim().toLowerCase(Locale.ROOT)
                : null;

        if (normalizedEmail != null && merchantRepositoryPort.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateMerchantEmailException("Merchant with email " + normalizedEmail + " already exists");
        }

        UUID id = idGenerator.generate();
        Instant createdAt = timeProvider.now();

        Merchant merchant = new Merchant(id, command.name(), command.email(), createdAt);

        merchantRepositoryPort.save(merchant);

        return merchant;
    }
}
