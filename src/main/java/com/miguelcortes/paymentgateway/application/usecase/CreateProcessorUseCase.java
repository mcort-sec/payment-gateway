package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateProcessorCommand;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Processor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CreateProcessorUseCase {

    private final ProcessorRepositoryPort processorRepositoryPort;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public CreateProcessorUseCase(
            ProcessorRepositoryPort processorRepositoryPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        this.processorRepositoryPort = processorRepositoryPort;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public Processor execute(CreateProcessorCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        UUID id = idGenerator.generate();
        Instant createdAt = timeProvider.now();

        Processor processor = new Processor(id, command.name(), createdAt);

        processorRepositoryPort.save(processor);

        return processor;
    }
}
