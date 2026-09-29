package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateApiCredentialCommand;
import com.miguelcortes.paymentgateway.application.dto.GeneratedApiCredential;
import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.exception.ApiCredentialGenerationException;
import com.miguelcortes.paymentgateway.application.exception.DuplicateKeyPrefixException;
import com.miguelcortes.paymentgateway.application.exception.MerchantNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.MerchantSuspendedException;
import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.Merchant;

import java.time.Instant;
import java.util.UUID;

public class CreateApiCredentialUseCase {

    private static final int MAX_ATTEMPTS = 3;

    private final ApiCredentialRepositoryPort apiCredentialRepositoryPort;
    private final MerchantRepositoryPort merchantRepositoryPort;
    private final ApiKeyGeneratorPort apiKeyGenerator;
    private final ApiKeyHasherPort apiKeyHasher;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public CreateApiCredentialUseCase(
            ApiCredentialRepositoryPort apiCredentialRepositoryPort,
            MerchantRepositoryPort merchantRepositoryPort,
            ApiKeyGeneratorPort apiKeyGenerator,
            ApiKeyHasherPort apiKeyHasher,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        this.apiCredentialRepositoryPort = apiCredentialRepositoryPort;
        this.merchantRepositoryPort = merchantRepositoryPort;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public GeneratedApiCredential execute(CreateApiCredentialCommand command) {
        Merchant merchant = merchantRepositoryPort.findById(command.merchantId())
                .orElseThrow(() -> new MerchantNotFoundException(command.merchantId()));

        if (!merchant.isActive()) {
            throw new MerchantSuspendedException(command.merchantId());
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            GeneratedApiKey generatedKey = apiKeyGenerator.generateTestKey();
            String keyHash = apiKeyHasher.hash(generatedKey.fullPlaintextApiKey());
            UUID id = idGenerator.generate();
            Instant now = timeProvider.now();

            ApiCredential credential = new ApiCredential(
                    id,
                    command.merchantId(),
                    generatedKey.keyPrefix(),
                    keyHash,
                    now
            );

            try {
                apiCredentialRepositoryPort.save(credential);
                return new GeneratedApiCredential(credential, generatedKey.fullPlaintextApiKey());
            } catch (DuplicateKeyPrefixException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ApiCredentialGenerationException(
                            "Failed to generate a unique API key credential after " + MAX_ATTEMPTS + " attempts",
                            ex
                    );
                }
            }
        }

        throw new ApiCredentialGenerationException("Unexpected failure during credential generation");
    }
}
