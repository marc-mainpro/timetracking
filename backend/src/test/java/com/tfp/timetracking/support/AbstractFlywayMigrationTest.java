package com.tfp.timetracking.support;

import java.util.Locale;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class AbstractFlywayMigrationTest extends SharedPostgresContainerSupport {

    protected DataSource dataSource;
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void migrateSchemaForTest(TestInfo testInfo) {
        String schema = schemaName(testInfo);
        JdbcTemplate adminJdbc = new JdbcTemplate(newDataSource(null));
        adminJdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");

        dataSource = newDataSource(schema);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(false)
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    private String schemaName(TestInfo testInfo) {
        String raw = getClass().getSimpleName() + "_" + testInfo.getTestMethod().map(method -> method.getName()).orElse("test");
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        String prefix = normalized.substring(0, Math.min(normalized.length(), 24));
        String suffix = Integer.toHexString(normalized.hashCode());
        return "fw_" + prefix + "_" + suffix;
    }
}
