package com.tfp.timetracking.timetracking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkdayRepositoryAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("timetracking")
            .withUsername("timetracking")
            .withPassword("timetracking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WorkdayRepository workdayRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndReloadsWorkdayWithBreaks() {
        UUID tenantId = insertTenant();
        UUID employeeId = insertUser(tenantId, "workday-persist@example.com");
        UUID workdayId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID breakId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Workday workday = Workday.start(tenantId, employeeId, Instant.parse("2026-01-15T09:00:00Z"), fixedIdGenerator(workdayId));
        workday.pullDomainEvents();
        workday.startBreak(Instant.parse("2026-01-15T12:00:00Z"), fixedIdGenerator(breakId));
        workday.endBreak(Instant.parse("2026-01-15T12:30:00Z"), fixedIdGenerator(UUID.randomUUID()));
        workday.close(Instant.parse("2026-01-15T18:00:00Z"), fixedIdGenerator(UUID.randomUUID()));

        workdayRepository.save(workday);

        Workday recovered = workdayRepository.findById(tenantId, workdayId).orElseThrow();
        assertThat(recovered.id()).isEqualTo(workdayId);
        assertThat(recovered.tenantId()).isEqualTo(tenantId);
        assertThat(recovered.employeeId()).isEqualTo(employeeId);
        assertThat(recovered.breaks()).hasSize(1);
        assertThat(recovered.breaks().get(0).id()).isEqualTo(breakId);
        assertThat(recovered.breaks().get(0).endedAt()).isEqualTo(Instant.parse("2026-01-15T12:30:00Z"));
        assertThat(recovered.endedAt()).isEqualTo(Instant.parse("2026-01-15T18:00:00Z"));
    }

    @Test
    void findByIdReturnsEmptyForWrongTenant() {
        UUID tenantA = insertTenant();
        UUID tenantB = insertTenant();
        UUID employeeId = insertUser(tenantA, "workday-tenant@example.com");
        Workday workday = Workday.start(tenantA, employeeId, Instant.now(), fixedIdGenerator(UUID.randomUUID()));
        workdayRepository.save(workday);

        assertThat(workdayRepository.findById(tenantA, workday.id())).isPresent();
        assertThat(workdayRepository.findById(tenantB, workday.id())).isEmpty();
    }

    @Test
    void findActiveByEmployeeReturnsOnlyActiveWorkdayForSameTenant() {
        UUID tenantA = insertTenant();
        UUID tenantB = insertTenant();
        UUID employeeId = insertUser(tenantA, "workday-active@example.com");
        Workday workday = Workday.start(tenantA, employeeId, Instant.now(), fixedIdGenerator(UUID.randomUUID()));
        workdayRepository.save(workday);

        assertThat(workdayRepository.findActiveByEmployee(tenantA, employeeId)).isPresent();
        assertThat(workdayRepository.findActiveByEmployee(tenantB, employeeId)).isEmpty();
    }

    @Test
    void savingStaleVersionThrowsOptimisticLockException() {
        UUID tenantId = insertTenant();
        UUID employeeId = insertUser(tenantId, "workday-lock@example.com");
        Workday original = Workday.start(tenantId, employeeId, Instant.parse("2026-01-15T09:00:00Z"), fixedIdGenerator(UUID.randomUUID()));
        Workday saved = workdayRepository.save(original);

        Workday firstCopy = workdayRepository.findById(tenantId, saved.id()).orElseThrow();
        Workday secondCopy = workdayRepository.findById(tenantId, saved.id()).orElseThrow();

        firstCopy.close(Instant.parse("2026-01-15T10:00:00Z"), fixedIdGenerator(UUID.randomUUID()));
        workdayRepository.save(firstCopy);

        secondCopy.close(Instant.parse("2026-01-15T11:00:00Z"), fixedIdGenerator(UUID.randomUUID()));

        assertThatThrownBy(() -> workdayRepository.save(secondCopy)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private UUID insertTenant() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, "Tenant " + id, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }

    private UUID insertUser(UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, email, "hash", "First", "Last", "ACTIVE", now, now);
        return id;
    }

    private static IdGenerator fixedIdGenerator(UUID firstId) {
        Deque<UUID> ids = new ArrayDeque<>();
        ids.add(firstId);
        return () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();
    }
}
