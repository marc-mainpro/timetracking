package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.support.AbstractFlywayMigrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * T704: verifica que {@code V9__processed_event.sql} deja la tabla de
 * deduplicacion del consumidor de demostracion con la forma esperada.
 */
class FlywayProcessedEventMigrationIntegrationTest extends AbstractFlywayMigrationTest {

    @Test
    void appliesProcessedEventMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "processed_event")).isTrue();
        }
    }

    @Test
    void processedEventTableHasExpectedColumns() {
        Long columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'processed_event' "
                        + "AND column_name IN ('event_id','processed_at')",
                Long.class);
        assertThat(columns).isEqualTo(2L);
    }

    @Test
    void eventIdIsThePrimaryKey() {
        Long primaryKeyColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = current_schema() AND table_name = 'processed_event' AND constraint_name = 'pk_processed_event' "
                        + "AND column_name = 'event_id'",
                Long.class);
        assertThat(primaryKeyColumns).isEqualTo(1L);
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
