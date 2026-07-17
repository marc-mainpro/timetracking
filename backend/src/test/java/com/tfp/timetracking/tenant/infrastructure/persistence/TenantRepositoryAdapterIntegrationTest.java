package com.tfp.timetracking.tenant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integracion (Testcontainers PostgreSQL) del adaptador
 * {@link TenantRepositoryAdapter}: persiste y recupera un Tenant a traves del
 * puerto {@link TenantRepository}, verificando el mapeo dominio&lt;-&gt;JPA
 * completo contra el esquema real de V2__identity.sql (ficha T202). Mismo
 * patron que {@code FlywayIdentityMigrationIT} (T201): contexto Spring Boot
 * completo levantado sobre un Postgres real de Testcontainers.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantRepositoryAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
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
    private TenantRepository tenantRepository;

    @Test
    void persistsAndRecoversTenantWithFullMapping() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        Tenant tenant = Tenant.reconstitute(id, "Acme Corp", TenantStatus.ACTIVE, "Europe/Madrid", now, now);

        tenantRepository.save(tenant);

        Tenant recovered = tenantRepository.findById(id).orElseThrow();
        assertThat(recovered.id()).isEqualTo(id);
        assertThat(recovered.name()).isEqualTo("Acme Corp");
        assertThat(recovered.timezone()).isEqualTo("Europe/Madrid");
        assertThat(recovered.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(recovered.createdAt()).isEqualTo(now);
        assertThat(recovered.updatedAt()).isEqualTo(now);
    }

    @Test
    void existsByIdReflectsPersistedState() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Tenant tenant = Tenant.reconstitute(id, "Beta Inc", TenantStatus.ACTIVE, "UTC", now, now);

        assertThat(tenantRepository.existsById(id)).isFalse();

        tenantRepository.save(tenant);

        assertThat(tenantRepository.existsById(id)).isTrue();
    }

    @Test
    void findByIdReturnsEmptyWhenTenantDoesNotExist() {
        assertThat(tenantRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void savingDeactivatedTenantPersistsInactiveStatus() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Tenant tenant = Tenant.reconstitute(id, "Gamma LLC", TenantStatus.ACTIVE, "UTC", now, now);
        tenant.deactivate(() -> now.plusSeconds(30));

        tenantRepository.save(tenant);

        Tenant recovered = tenantRepository.findById(id).orElseThrow();
        assertThat(recovered.status()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(recovered.isActive()).isFalse();
    }
}
