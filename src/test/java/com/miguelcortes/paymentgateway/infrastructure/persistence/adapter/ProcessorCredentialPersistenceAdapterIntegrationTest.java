package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateProcessorKeyPrefixException;
import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.ProcessorCredentialMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.ProcessorMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ProcessorCredentialPersistenceAdapter.class,
        ProcessorCredentialMapper.class,
        ProcessorPersistenceAdapter.class,
        ProcessorMapper.class
})
@Testcontainers
class ProcessorCredentialPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ProcessorCredentialPersistenceAdapter credentialAdapter;

    @Autowired
    private ProcessorPersistenceAdapter processorAdapter;

    private static final String HASH_1 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String HASH_2 = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";

    @Test
    @DisplayName("Should save and retrieve ProcessorCredential by id and by keyPrefix")
    void shouldSaveAndRetrieveProcessorCredentialByIdAndPrefix() {
        UUID processorId = UUID.randomUUID();
        processorAdapter.save(new Processor(processorId, "Proc 1", Instant.now()));

        UUID credentialId = UUID.randomUUID();
        String prefix = "prefProc0001";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        ProcessorCredential credential = new ProcessorCredential(
                credentialId,
                processorId,
                prefix,
                HASH_1,
                createdAt
        );

        credentialAdapter.save(credential);

        Optional<ProcessorCredential> byId = credentialAdapter.findById(credentialId);
        assertTrue(byId.isPresent());
        ProcessorCredential savedById = byId.get();
        assertEquals(credentialId, savedById.getId());
        assertEquals(processorId, savedById.getProcessorId());
        assertEquals(prefix, savedById.getKeyPrefix());
        assertEquals(HASH_1, savedById.getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, savedById.getStatus());
        assertEquals(createdAt, savedById.getCreatedAt());
        assertNull(savedById.getRevokedAt());

        Optional<ProcessorCredential> byPrefix = credentialAdapter.findByKeyPrefix(prefix);
        assertTrue(byPrefix.isPresent());
        assertEquals(credentialId, byPrefix.get().getId());
    }

    @Test
    @DisplayName("Should translate unique key_prefix constraint violation to DuplicateProcessorKeyPrefixException")
    void shouldTranslateDuplicateProcessorKeyPrefixConstraint() {
        UUID processorId = UUID.randomUUID();
        processorAdapter.save(new Processor(processorId, "Proc 2", Instant.now()));

        String duplicatePrefix = "prefDuplicate";

        ProcessorCredential cred1 = new ProcessorCredential(
                UUID.randomUUID(),
                processorId,
                duplicatePrefix,
                HASH_1,
                Instant.now()
        );
        credentialAdapter.save(cred1);

        ProcessorCredential cred2 = new ProcessorCredential(
                UUID.randomUUID(),
                processorId,
                duplicatePrefix,
                HASH_2,
                Instant.now()
        );

        DuplicateProcessorKeyPrefixException ex = assertThrows(
                DuplicateProcessorKeyPrefixException.class,
                () -> credentialAdapter.save(cred2)
        );

        assertTrue(ex.getMessage().contains(duplicatePrefix));
    }

    @Test
    @DisplayName("Should throw DataIntegrityViolationException on invalid foreign key and NOT translate to DuplicateProcessorKeyPrefixException")
    void shouldPropagateDataIntegrityViolationOnInvalidProcessorForeignKey() {
        UUID nonExistentProcessorId = UUID.randomUUID();

        ProcessorCredential orphanCredential = new ProcessorCredential(
                UUID.randomUUID(),
                nonExistentProcessorId,
                "orphanPrefix1",
                HASH_1,
                Instant.now()
        );

        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> credentialAdapter.save(orphanCredential)
        );

        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("fk_processor_credentials_processors")
                || (ex.getCause() != null && ex.getCause().getMessage().contains("fk_processor_credentials_processors")));
    }

    @Test
    @DisplayName("Should return empty Optional when searching for non-existent prefix")
    void shouldReturnEmptyForNonExistentPrefix() {
        Optional<ProcessorCredential> result = credentialAdapter.findByKeyPrefix("nonExistentPrf");
        assertTrue(result.isEmpty());
    }
}
