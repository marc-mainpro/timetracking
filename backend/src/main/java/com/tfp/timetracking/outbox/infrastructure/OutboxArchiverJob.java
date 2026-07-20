package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.ArchivePublishedOutboxMessages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diario (T703) que purga los mensajes {@code PUBLISHED} antiguos via
 * {@link ArchivePublishedOutboxMessages}. Cron configurable en {@code
 * outbox.archive-cron} (por defecto 03:00 todos los dias).
 *
 * <p>Mismo interruptor {@code outbox.scheduler-enabled} que {@link
 * OutboxPublisherJob} (desactivado en el perfil {@code test}).
 */
@Component
@ConditionalOnProperty(prefix = "outbox", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxArchiverJob {

    private final ArchivePublishedOutboxMessages archivePublishedOutboxMessages;

    public OutboxArchiverJob(ArchivePublishedOutboxMessages archivePublishedOutboxMessages) {
        this.archivePublishedOutboxMessages = archivePublishedOutboxMessages;
    }

    @Scheduled(cron = "${outbox.archive-cron:0 0 3 * * *}")
    public void archivePublished() {
        archivePublishedOutboxMessages.archive();
    }
}
