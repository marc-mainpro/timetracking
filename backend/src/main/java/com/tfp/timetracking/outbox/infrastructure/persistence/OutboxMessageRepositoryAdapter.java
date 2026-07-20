package com.tfp.timetracking.outbox.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    public void markFailed(UUID id, String lastError) {
        jpaRepository.markFailed(id, lastError);
    }

    @Override
    @Transactional
    public int archivePublishedBefore(Instant before) {
        return jpaRepository.archivePublishedBefore(before);
    }
}
