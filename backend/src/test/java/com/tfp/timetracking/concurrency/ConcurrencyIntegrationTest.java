package com.tfp.timetracking.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.corrections.infrastructure.persistence.CorrectionRequestRepositoryAdapter;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionRequestDto;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionResolutionRequest;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import com.tfp.timetracking.timetracking.infrastructure.persistence.WorkdayRepositoryAdapter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestTenantFactory testTenantFactory;

    @Autowired
    private RepositoryConcurrencyGate concurrencyGate;

    @AfterEach
    void clearGates() {
        concurrencyGate.reset();
    }

    @Test
    void doubleWorkdayCloseAllowsExactlyOneWinner() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("close-race");
        String workdayId = startWorkday(tenant.employee().token());
        concurrencyGate.arm("workday.findActiveByEmployee", 2);

        List<MvcResult> results = runConcurrently(
                () -> performAuthenticatedPost("/api/v1/workdays/current/end", tenant.employee().token()),
                () -> performAuthenticatedPost("/api/v1/workdays/current/end", tenant.employee().token()));

        assertThat(statuses(results)).containsExactlyInAnyOrder(200, 409);
        assertThat(errorCodes(results)).contains("CONCURRENT_MODIFICATION");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from workday where id = ?::uuid and status = 'CLOSED'",
                        Integer.class,
                        workdayId))
                .isEqualTo(1);
    }

    @Test
    void doubleCorrectionApprovalAdjustsOnceAndAuditsOnce() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("approve-race");
        String workdayId = startWorkday(tenant.employee().token());
        closeCurrentWorkday(tenant.employee().token());
        String correctionId = requestCorrection(tenant.employee().token(), workdayId);
        concurrencyGate.arm("correction.findById", 2);
        concurrencyGate.arm("workday.findById", 2);

        List<MvcResult> results = runConcurrently(
                () -> approveCorrection(correctionId, tenant.admin().token()),
                () -> approveCorrection(correctionId, tenant.admin().token()));

        assertThat(statuses(results)).containsExactlyInAnyOrder(200, 409);
        assertThat(errorCodes(results)).contains("CONCURRENT_MODIFICATION");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from audit_event where action = 'CORRECTION_APPROVED' and entity_id = ?::uuid",
                        Integer.class,
                        correctionId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select version from workday where id = ?::uuid", Long.class, workdayId))
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                        "select status from correction_request where id = ?::uuid",
                        String.class,
                        correctionId))
                .isEqualTo("APPROVED");
    }

    @Test
    void doubleWorkdayStartCreatesOnlyOneActiveWorkday() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("start-race");
        concurrencyGate.arm("workday.findActiveByEmployee", 2);

        List<MvcResult> results = runConcurrently(
                () -> performAuthenticatedPost("/api/v1/workdays/start", tenant.employee().token()),
                () -> performAuthenticatedPost("/api/v1/workdays/start", tenant.employee().token()));

        assertThat(statuses(results)).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from workday where tenant_id = ?::uuid and employee_id = ?::uuid and status in ('OPEN','ON_BREAK')",
                        Integer.class,
                        tenant.tenantId(),
                        tenant.employee().userId()))
                .isEqualTo(1);
    }

    @Test
    void breakStartAndCloseRaceLeavesConsistentFinalState() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("break-close-race");
        String workdayId = startWorkday(tenant.employee().token());
        concurrencyGate.arm("workday.findActiveByEmployee", 2);

        List<MvcResult> results = runConcurrently(
                () -> performAuthenticatedPost("/api/v1/workdays/current/breaks/start", tenant.employee().token()),
                () -> performAuthenticatedPost("/api/v1/workdays/current/end", tenant.employee().token()));

        assertThat(statuses(results)).containsExactlyInAnyOrder(200, 409);
        assertThat(errorCodes(results)).contains("CONCURRENT_MODIFICATION");

        String status = jdbcTemplate.queryForObject("select status from workday where id = ?::uuid", String.class, workdayId);
        assertThat(status).isIn("ON_BREAK", "CLOSED");

        if ("ON_BREAK".equals(status)) {
            JsonNode current = objectMapper.readTree(mockMvc.perform(get("/api/v1/workdays/current")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            assertThat(current.get("breaks").size()).isEqualTo(1);
            assertThat(current.get("breaks").get(0).get("endedAt").isNull()).isTrue();
        }
    }

    private String startWorkday(String token) throws Exception {
        return objectMapper.readTree(performAuthenticatedPost("/api/v1/workdays/start", token)
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
    }

    private void closeCurrentWorkday(String token) throws Exception {
        performAuthenticatedPost("/api/v1/workdays/current/end", token);
    }

    private String requestCorrection(String token, String workdayId) throws Exception {
        CorrectionRequestDto request = new CorrectionRequestDto(
                "Ajuste",
                new CorrectionRequestDto.ProposedChangesDto(
                        Instant.parse("2026-01-15T09:05:00Z"),
                        Instant.parse("2026-01-15T18:05:00Z"),
                        List.of(new CorrectionRequestDto.ProposedBreakDto(
                                Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z")))));
        String response = mockMvc.perform(post("/api/v1/workdays/{workdayId}/corrections", workdayId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private MvcResult approveCorrection(String correctionId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/corrections/{correctionId}/approve", correctionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CorrectionResolutionRequest("Aprobada"))))
                .andReturn();
    }

    private MvcResult performAuthenticatedPost(String path, String token) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private List<MvcResult> runConcurrently(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> firstResult = executor.submit(first);
            Future<MvcResult> secondResult = executor.submit(second);
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Integer> statuses(List<MvcResult> results) {
        return results.stream().map(result -> result.getResponse().getStatus()).toList();
    }

    private List<String> errorCodes(List<MvcResult> results) throws Exception {
        java.util.ArrayList<String> errorCodes = new java.util.ArrayList<>();
        for (MvcResult result : results) {
            if (result.getResponse().getStatus() >= 400 && !result.getResponse().getContentAsString().isBlank()) {
                errorCodes.add(objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText());
            }
        }
        return errorCodes;
    }

    static final class RepositoryConcurrencyGate {

        private final Map<String, GateState> states = new ConcurrentHashMap<>();

        void arm(String key, int parties) {
            states.put(key, new GateState(new CyclicBarrier(parties), new AtomicInteger(parties)));
        }

        void await(String key) {
            GateState state = states.get(key);
            if (state == null) {
                return;
            }
            try {
                state.barrier.await(10, TimeUnit.SECONDS);
                if (state.remaining.decrementAndGet() == 0) {
                    states.remove(key, state);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudo sincronizar el punto de concurrencia " + key, ex);
            }
        }

        void reset() {
            states.clear();
        }

        private record GateState(CyclicBarrier barrier, AtomicInteger remaining) {}
    }

    static final class GatedWorkdayRepository implements WorkdayRepository {

        private final WorkdayRepositoryAdapter delegate;
        private final RepositoryConcurrencyGate gate;

        GatedWorkdayRepository(WorkdayRepositoryAdapter delegate, RepositoryConcurrencyGate gate) {
            this.delegate = delegate;
            this.gate = gate;
        }

        @Override
        public Workday save(Workday workday) {
            return delegate.save(workday);
        }

        @Override
        public Optional<Workday> findById(UUID tenantId, UUID id) {
            Optional<Workday> result = delegate.findById(tenantId, id);
            gate.await("workday.findById");
            return result;
        }

        @Override
        public Optional<Workday> findActiveByEmployee(UUID tenantId, UUID employeeId) {
            Optional<Workday> result = delegate.findActiveByEmployee(tenantId, employeeId);
            gate.await("workday.findActiveByEmployee");
            return result;
        }

        @Override
        public PagedResult<Workday> findByEmployee(UUID tenantId, UUID employeeId, Instant from, Instant to, int page, int size) {
            return delegate.findByEmployee(tenantId, employeeId, from, to, page, size);
        }

        @Override
        public PagedResult<Workday> findByTenant(UUID tenantId, UUID employeeId, Instant from, Instant to, int page, int size) {
            return delegate.findByTenant(tenantId, employeeId, from, to, page, size);
        }
    }

    static final class GatedCorrectionRequestRepository implements CorrectionRequestRepository {

        private final CorrectionRequestRepositoryAdapter delegate;
        private final RepositoryConcurrencyGate gate;

        GatedCorrectionRequestRepository(CorrectionRequestRepositoryAdapter delegate, RepositoryConcurrencyGate gate) {
            this.delegate = delegate;
            this.gate = gate;
        }

        @Override
        public CorrectionRequest save(CorrectionRequest correctionRequest) {
            return delegate.save(correctionRequest);
        }

        @Override
        public Optional<CorrectionRequest> findById(UUID tenantId, UUID id) {
            Optional<CorrectionRequest> result = delegate.findById(tenantId, id);
            gate.await("correction.findById");
            return result;
        }

        @Override
        public Optional<CorrectionRequest> findPendingByWorkdayAndRequestedBy(UUID tenantId, UUID workdayId, UUID requestedBy) {
            return delegate.findPendingByWorkdayAndRequestedBy(tenantId, workdayId, requestedBy);
        }

        @Override
        public PagedResult<CorrectionRequest> findByTenant(UUID tenantId, CorrectionRequestStatus status, int page, int size) {
            return delegate.findByTenant(tenantId, status, page, size);
        }

        @Override
        public PagedResult<CorrectionRequest> findByRequestedBy(
                UUID tenantId, UUID requestedBy, CorrectionRequestStatus status, int page, int size) {
            return delegate.findByRequestedBy(tenantId, requestedBy, status, page, size);
        }
    }

    @TestConfiguration
    static class ConcurrencyIntegrationTestConfiguration {

        @Bean
        RepositoryConcurrencyGate repositoryConcurrencyGate() {
            return new RepositoryConcurrencyGate();
        }

        @Bean
        @Primary
        WorkdayRepository workdayRepository(WorkdayRepositoryAdapter delegate, RepositoryConcurrencyGate gate) {
            return new GatedWorkdayRepository(delegate, gate);
        }

        @Bean
        @Primary
        CorrectionRequestRepository correctionRequestRepository(
                CorrectionRequestRepositoryAdapter delegate, RepositoryConcurrencyGate gate) {
            return new GatedCorrectionRequestRepository(delegate, gate);
        }

        @Bean
        TestTenantFactory testTenantFactory(
                MockMvc mockMvc,
                ObjectMapper objectMapper,
                UserRepository userRepository,
                PasswordHasher passwordHasher,
                Clock clock,
                IdGenerator idGenerator) {
            return new TestTenantFactory(mockMvc, objectMapper, userRepository, passwordHasher, clock, idGenerator);
        }
    }
}
