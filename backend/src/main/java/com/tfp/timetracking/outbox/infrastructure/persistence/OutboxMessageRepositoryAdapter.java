package com.tfp.timetracking.outbox.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxMessageRepositoryAdapter implements OutboxMessageRepository {

    private final OutboxMessageJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public OutboxMessageRepositoryAdapter(OutboxMessageJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public OutboxMessage save(OutboxMessage message) {
        return OutboxMessageMapper.toDomain(
                jpaRepository.save(OutboxMessageMapper.toJpaEntity(message, objectMapper)), objectMapper);
    }

    @Override
    public Optional<OutboxMessage> findById(UUID id) {
        return jpaRepository.findById(id).map(entity -> OutboxMessageMapper.toDomain(entity, objectMapper));
    }

    @Override
    @Transactional
    public List<OutboxMessage> claimBatch(int limit, Instant now, Instant leaseExpiresAt) {
        return jpaRepository.claimBatch(now, limit, leaseExpiresAt).stream()
                .map(entity -> OutboxMessageMapper.toDomain(entity, objectMapper))
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id, Instant publishedAt) {
        jpaRepository.markPublished(id, publishedAt);
    }

    @Override
    @Transactional
    public void markRetry(UUID id, int attempts, Instant nextAttemptAt, String lastError) {
        jpaRepository.markRetry(id, attempts, nextAttemptAt, lastError);
    }

    @Override
    @Transactional
    public void markFailed(UUID id, int attempts, String lastError) {
        jpaRepository.markFailed(id, attempts, lastError);
    }

    @Override
    @Transactional
    public int archivePublishedBefore(Instant before) {
        return jpaRepository.archivePublishedBefore(before);
    }

    @Override
    public PagedResult<OutboxMessage> findByStatus(OutboxMessageStatus status, int page, int size) {
        Page<OutboxMessageJpaEntity> found =
                jpaRepository.findByStatusOrderByCreatedAtAsc(status.name(), PageRequest.of(page, size));
        return new PagedResult<>(
                found.getContent().stream()
                        .map(entity -> OutboxMessageMapper.toDomain(entity, objectMapper))
                        .toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }

    @Override
    @Transactional
    public boolean requeueFailed(UUID id) {
        return jpaRepository.requeueFailed(id) > 0;
    }

    @Override
    @Transactional
    public boolean discardFailed(UUID id) {
        return jpaRepository.discardFailed(id) > 0;
    }

    @Override
    public long countPending() {
        return jpaRepository.countPending();
    }

    @Override
    public long countFailed() {
        return jpaRepository.countFailed();
    }
}
