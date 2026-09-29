package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateProcessorCredentialCommand;
import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.dto.GeneratedProcessorCredential;
import com.miguelcortes.paymentgateway.application.exception.DuplicateProcessorKeyPrefixException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorCredentialGenerationException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorSuspendedException;
import com.miguelcortes.paymentgateway.application.model.ApiKeyType;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CreateProcessorCredentialUseCase {

    private static final int MAX_ATTEMPTS = 3;

    private final ProcessorCredentialRepositoryPort processorCredentialRepositoryPort;
    private final ProcessorRepositoryPort processorRepositoryPort;
    private final ApiKeyGeneratorPort apiKeyGenerator;
    private final ApiKeyHasherPort apiKeyHasher;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public CreateProcessorCredentialUseCase(
            ProcessorCredentialRepositoryPort processorCredentialRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort,
            ApiKeyGeneratorPort apiKeyGenerator,
            ApiKeyHasherPort apiKeyHasher,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        this.processorCredentialRepositoryPort = processorCredentialRepositoryPort;
        this.processorRepositoryPort = processorRepositoryPort;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public GeneratedProcessorCredential execute(CreateProcessorCredentialCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Processor processor = processorRepositoryPort.findById(command.processorId())
                .orElseThrow(() -> new ProcessorNotFoundException(command.processorId()));

        if (!processor.isActive()) {
            throw new ProcessorSuspendedException(command.processorId());
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            GeneratedApiKey generatedKey = apiKeyGenerator.generate(ApiKeyType.PROCESSOR);
            String keyHash = apiKeyHasher.hash(generatedKey.plaintextApiKey());
            UUID id = idGenerator.generate();
            Instant now = timeProvider.now();

            ProcessorCredential credential = new ProcessorCredential(
                    id,
                    command.processorId(),
                    generatedKey.keyPrefix(),
                    keyHash,
                    now
            );

            try {
                processorCredentialRepositoryPort.save(credential);
                return new GeneratedProcessorCredential(credential, generatedKey.plaintextApiKey());
            } catch (DuplicateProcessorKeyPrefixException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ProcessorCredentialGenerationException(
                            "Failed to generate a unique API key credential after " + MAX_ATTEMPTS + " attempts",
                            ex
                    );
                }
            }
        }

        throw new ProcessorCredentialGenerationException("Unexpected failure during credential generation");
    }
}
