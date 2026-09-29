package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidProcessorException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Processor {

    private final UUID id;
    private String name;
    private ProcessorStatus status;
    private final Instant createdAt;

    public Processor(UUID id, String name, Instant createdAt) {
        this(id, name, ProcessorStatus.ACTIVE, createdAt);
    }

    public Processor(UUID id, String name, ProcessorStatus status, Instant createdAt) {
        validateInvariants(id, name, status, createdAt);
        this.id = id;
        this.name = name.trim();
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Processor reconstitute(UUID id, String name, ProcessorStatus status, Instant createdAt) {
        return new Processor(id, name, status, createdAt);
    }

    public void suspend() {
        this.status = ProcessorStatus.SUSPENDED;
    }

    public void activate() {
        this.status = ProcessorStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == ProcessorStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return this.status == ProcessorStatus.SUSPENDED;
    }

    private static void validateInvariants(UUID id, String name, ProcessorStatus status, Instant createdAt) {
        if (id == null) {
            throw new InvalidProcessorException("Processor ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidProcessorException("Processor name cannot be null or blank");
        }
        if (name.trim().length() > 100) {
            throw new InvalidProcessorException("Processor name cannot exceed 100 characters");
        }
        if (status == null) {
            throw new InvalidProcessorException("Processor status cannot be null");
        }
        if (createdAt == null) {
            throw new InvalidProcessorException("CreatedAt timestamp cannot be null");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ProcessorStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Processor processor)) return false;
        return Objects.equals(id, processor.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
