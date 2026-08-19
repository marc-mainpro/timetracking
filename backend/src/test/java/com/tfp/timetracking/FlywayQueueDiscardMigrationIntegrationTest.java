package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.support.AbstractFlywayMigrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V27: supresion logica de elementos fallidos de las colas. */
class FlywayQueueDiscardMigrationIntegrationTest extends AbstractFlywayMigrationTest {

    @Test
    void notificationStatusCheckAcceptsDiscarded() {
        // Sin ampliar el CHECK, descartar una notificacion reventaria en la
        // base de datos en vez de en el dominio: la insercion es la asercion.
        insertNotification("DISCARDED");

        assertThat(countByStatus("DISCARDED")).isEqualTo(1L);
    }

    @Test
    void notificationStatusCheckStillRejectsUnknownStates() {
        assertThatThrownBy(() -> insertNotification("INVENTADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createsPartialIndexesForTheFailedListings() {
        // El panel lista los fallidos por antiguedad; el indice de outbox que ya
        // existia sirve a la reclamacion del publicador, no a esta consulta.
        assertThat(indexCount("outbox_message", "ix_outbox_message_failed")).isEqualTo(1L);
        assertThat(indexCount("notification", "ix_notification_failed")).isEqualTo(1L);
    }

    private Long countByStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE status = ?", Long.class, status);
    }

    private Long indexCount(String table, String index) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() "
                        + "AND tablename = ? AND indexname = ?",
                Long.class,
                table,
                index);
    }

    private void insertNotification(String status) {
        jdbcTemplate.update(
                """
                INSERT INTO notification (id, tenant_id, recipient_user_id, recipient_email, type, title, body,
                                          status, attempts, created_at, email_required)
                VALUES (?, ?, ?, ?, 'CORRECTION_APPROVED', 'titulo', 'cuerpo', ?, 1, NOW(), TRUE)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "empleado@acme.test",
                status);
    }
}
