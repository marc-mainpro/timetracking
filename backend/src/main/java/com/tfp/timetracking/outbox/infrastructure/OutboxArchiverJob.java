package com.tfp.timetracking.outbox.infrastructure;

import com.tfp.timetracking.outbox.application.ArchivePublishedOutboxMessages;
import com.tfp.timetracking.shared.application.ScheduledJobRunner;
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
 *
 * <p>Se ejecuta a traves de {@link ScheduledJobRunner} (T140-02/04) para que la
 * purga tenga su propio {@code correlationId} en los logs y quede contada en
 * las metricas de jobs.
 */
@Component
@ConditionalOnProperty(prefix = "outbox", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxArchiverJob {

    /** Nombre estable del job: tag {@code job} de las metricas y sufijo del {@code useCase}. */
    public static final String JOB_NAME = "outbox-archiver";

    private final ArchivePublishedOutboxMessages archivePublishedOutboxMessages;
    private final ScheduledJobRunner scheduledJobRunner;

    public OutboxArchiverJob(
            ArchivePublishedOutboxMessages archivePublishedOutboxMessages, ScheduledJobRunner scheduledJobRunner) {
        this.archivePublishedOutboxMessages = archivePublishedOutboxMessages;
        this.scheduledJobRunner = scheduledJobRunner;
    }

    @Scheduled(cron = "${outbox.archive-cron:0 0 3 * * *}")
    public void archivePublished() {
        scheduledJobRunner.run(JOB_NAME, archivePublishedOutboxMessages::archive);
    }
}
