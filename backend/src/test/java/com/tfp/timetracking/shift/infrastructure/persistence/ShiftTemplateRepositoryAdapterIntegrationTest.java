package com.tfp.timetracking.shift.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.support.AbstractPostgresDataJpaTest;
import com.tfp.timetracking.shift.domain.model.ShiftBreakPolicy;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import(ShiftTemplateRepositoryAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ShiftTemplateRepositoryAdapterIntegrationTest extends AbstractPostgresDataJpaTest {

    @Autowired
    private ShiftTemplateRepositoryAdapter repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndFindsByTenantScopedName() {
        UUID tenantId = insertTenant();
        ShiftTemplate template = ShiftTemplate.create(
                tenantId,
                "General",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                new ShiftBreakPolicy(Duration.ofMinutes(30)),
                UUID.randomUUID());

        repository.save(template);

        assertThat(repository.findByName(tenantId, "General")).isPresent();
        assertThat(repository.findByTenantId(tenantId)).hasSize(1);
    }

    private UUID insertTenant() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, "Tenant " + id, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }
}
