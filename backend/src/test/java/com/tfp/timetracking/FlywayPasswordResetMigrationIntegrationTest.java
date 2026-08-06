package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.support.AbstractFlywayMigrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class FlywayPasswordResetMigrationIntegrationTest extends AbstractFlywayMigrationTest {

    @Test
    void createsPasswordResetTokenTable() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "password_reset_token")).isTrue();
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }
}
