package com.tfp.timetracking.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.domain.TenantAccessRepository;
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
 * Verifica que el puerto de identidad {@link TenantAccessRepository} (usado por
 * login, refresh y la comprobación por petición) considera operativos únicamente
 * a los tenants {@code ACTIVE}: los estados del ciclo de vida V2
 * {@code PENDING}, {@code SUSPENDED} y {@code ARCHIVED} bloquean el acceso
 * (RF-TEN-009, T50-03).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantAccessRepositoryAdapterIntegrationTest {

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

    @Autowired
    private TenantAccessRepository tenantAccessRepository;

    @Test
    void onlyActiveTenantsCanOperate() {
        assertThat(tenantAccessRepository.isActive(persistTenantWith(TenantStatus.ACTIVE))).isTrue();
        assertThat(tenantAccessRepository.isActive(persistTenantWith(TenantStatus.PENDING))).isFalse();
        assertThat(tenantAccessRepository.isActive(persistTenantWith(TenantStatus.SUSPENDED))).isFalse();
        assertThat(tenantAccessRepository.isActive(persistTenantWith(TenantStatus.ARCHIVED))).isFalse();
    }

    @Test
    void unknownTenantIsNotActive() {
        assertThat(tenantAccessRepository.isActive(UUID.randomUUID())).isFalse();
    }

    private UUID persistTenantWith(TenantStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        Tenant tenant = Tenant.reconstitute(id, "Tenant " + status, status, "Europe/Madrid", now, now, now, null, null, null);
        tenantRepository.save(tenant);
        return id;
    }
}
