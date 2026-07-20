package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso operativo (T703, job diario): purga fisicamente los mensajes
 * {@code PUBLISHED} cuyo {@code publishedAt} sea anterior a {@code
 * outbox.archive-retention} (30 dias por defecto). Nunca toca mensajes
 * {@code PENDING}, {@code PROCESSING} ni {@code FAILED}: {@link
 * OutboxMessageRepository#archivePublishedBefore} (T701) solo borra filas
 * {@code PUBLISHED}.
 */
@Service
public class ArchivePublishedOutboxMessages {

    private static final Logger log = LoggerFactory.getLogger(ArchivePublishedOutboxMessages.class);

    private final OutboxMessageRepository repository;
    private final Clock clock;
    private final OutboxProperties properties;

    public ArchivePublishedOutboxMessages(OutboxMessageRepository repository, Clock clock, OutboxProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public int archive() {
        int archived = repository.archivePublishedBefore(clock.now().minus(properties.archiveRetention()));
        if (archived > 0) {
            log.info("outbox.archive archived={}", archived);
        }
        return archived;
    }
}
