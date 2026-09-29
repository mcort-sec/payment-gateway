package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateProcessorCommand;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateProcessorUseCaseTest {

    private InMemoryProcessorRepository fakeRepository;
    private FakeIdGenerator fakeIdGenerator;
    private FakeTimeProvider fakeTimeProvider;
    private CreateProcessorUseCase useCase;

    private static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant FIXED_TIME = Instant.parse("2026-09-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        fakeRepository = new InMemoryProcessorRepository();
        fakeIdGenerator = new FakeIdGenerator(FIXED_ID);
        fakeTimeProvider = new FakeTimeProvider(FIXED_TIME);
        useCase = new CreateProcessorUseCase(fakeRepository, fakeIdGenerator, fakeTimeProvider);
    }

    @Test
    @DisplayName("Should create and save new active processor successfully")
    void shouldCreateAndSaveNewProcessorSuccessfully() {
        CreateProcessorCommand command = new CreateProcessorCommand("Visa Direct");

        Processor createdProcessor = useCase.execute(command);

        assertEquals(FIXED_ID, createdProcessor.getId());
        assertEquals("Visa Direct", createdProcessor.getName());
        assertEquals(ProcessorStatus.ACTIVE, createdProcessor.getStatus());
        assertTrue(createdProcessor.isActive());
        assertEquals(FIXED_TIME, createdProcessor.getCreatedAt());

        assertEquals(1, fakeRepository.saveCallCount);
        assertSame(createdProcessor, fakeRepository.lastSavedProcessor);
        assertEquals(1, fakeIdGenerator.callCount);
        assertEquals(1, fakeTimeProvider.callCount);
    }

    // --- Fakes Manuales ---

    private static class InMemoryProcessorRepository implements ProcessorRepositoryPort {
        private final Map<UUID, Processor> storageById = new HashMap<>();
        int saveCallCount = 0;
        Processor lastSavedProcessor;

        @Override
        public void save(Processor processor) {
            saveCallCount++;
            lastSavedProcessor = processor;
            storageById.put(processor.getId(), processor);
        }

        @Override
        public Optional<Processor> findById(UUID id) {
            return Optional.ofNullable(storageById.get(id));
        }
    }

    private static class FakeIdGenerator implements IdGenerator {
        private final UUID fixedId;
        int callCount = 0;

        FakeIdGenerator(UUID fixedId) {
            this.fixedId = fixedId;
        }

        @Override
        public UUID generate() {
            callCount++;
            return fixedId;
        }
    }

    private static class FakeTimeProvider implements TimeProvider {
        private final Instant fixedTime;
        int callCount = 0;

        FakeTimeProvider(Instant fixedTime) {
            this.fixedTime = fixedTime;
        }

        @Override
        public Instant now() {
            callCount++;
            return fixedTime;
        }
    }
}
