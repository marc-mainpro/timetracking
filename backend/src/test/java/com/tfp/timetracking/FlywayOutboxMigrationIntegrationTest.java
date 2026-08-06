package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.support.AbstractFlywayMigrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class FlywayOutboxMigrationIntegrationTest extends AbstractFlywayMigrationTest {

    @Test
    void appliesOutboxMigrationFromEmptyDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "outbox_message")).isTrue();
        }
    }

    @Test
    void createsCompositeIndexForPoller() {
        Long indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND tablename = 'outbox_message' "
                        + "AND indexname = 'ix_outbox_message_status_next_attempt_at'",
                Long.class);
        assertThat(indexCount).isEqualTo(1L);
    }

    @Test
    void outboxTableHasExpectedColumns() {
        Long columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'outbox_message' "
                        + "AND column_name IN ('id','tenant_id','aggregate_type','aggregate_id','event_type',"
                        + "'event_version','payload','occurred_at','published_at','attempts','next_attempt_at',"
                        + "'last_error','status','created_at')",
                Long.class);
        assertThat(columns).isEqualTo(14L);
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
