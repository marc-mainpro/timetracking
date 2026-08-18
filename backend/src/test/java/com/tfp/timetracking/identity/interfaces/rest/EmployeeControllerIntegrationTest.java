package com.tfp.timetracking.identity.interfaces.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class EmployeeControllerIntegrationTest {

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
    private TestTenantFactory testTenantFactory;

    @Test
    void performsAdminCrudForEmployees() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("employees-crud");
        CreateEmployeeRequest createRequest = new CreateEmployeeRequest(
                "new.employee." + UUID.randomUUID() + "@acme.test",
                "supersecretpwd",
                "New",
                "Employee",
                Set.of("EMPLOYEE"));

        MvcResult created = mockMvc.perform(post("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(createRequest.email()))
                .andReturn();

        String employeeId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));

        mockMvc.perform(get("/api/v1/employees/{employeeId}", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId));

        mockMvc.perform(put("/api/v1/employees/{employeeId}", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateEmployeeRequest("Updated", "Name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));

        mockMvc.perform(put("/api/v1/employees/{employeeId}/roles", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRolesRequest(Set.of("EMPLOYEE", "TENANT_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(2)));

        mockMvc.perform(patch("/api/v1/employees/{employeeId}/deactivate", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(patch("/api/v1/employees/{employeeId}/activate", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listFilteredByRoleOnlyReturnsUsersWithThatRole() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("employees-role-filter");

        // Sin filtro el listado sigue siendo el de la gestion de usuarios, que
        // debe seguir viendo tambien a los administradores que no fichan.
        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/v1/employees")
                        .param("role", "EMPLOYEE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(tenant.employee().userId().toString()));
    }

    @Test
    void listIncludesAdminsThatAreAlsoEmployees() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("employees-role-both");
        CreateEmployeeRequest createRequest = new CreateEmployeeRequest(
                "admin.employee." + UUID.randomUUID() + "@acme.test",
                "supersecretpwd",
                "Admin",
                "Employee",
                Set.of("EMPLOYEE", "TENANT_ADMIN"));

        MvcResult created = mockMvc.perform(post("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String bothRolesId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/employees")
                        .param("role", "EMPLOYEE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(bothRolesId)));
    }

    @Test
    void rejectsUnknownAndPlatformRolesInTheFilter() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("employees-role-invalid");

        mockMvc.perform(get("/api/v1/employees")
                        .param("role", "NO_EXISTE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest());

        // Filtrar por un rol de plataforma dentro de un tenant no tiene
        // significado: no debe parecer una consulta valida que no encuentra a nadie.
        mockMvc.perform(get("/api/v1/employees")
                        .param("role", "PLATFORM_ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleFilterNeverCrossesTenants() throws Exception {
        TestTenantFactory.TenantActors first = testTenantFactory.createTenantActors("employees-role-tenant-a");
        TestTenantFactory.TenantActors second = testTenantFactory.createTenantActors("employees-role-tenant-b");

        mockMvc.perform(get("/api/v1/employees")
                        .param("role", "EMPLOYEE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(first.employee().userId().toString()))
                .andExpect(jsonPath("$.content[*].id",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(second.employee().userId().toString()))));
    }

    @Test
    void employeeCannotAccessAdminEndpoints() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("employees-forbidden");

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidPaginationParameters() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("employees-pagination");

        mockMvc.perform(get("/api/v1/employees")
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/employees")
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void cannotRemoveLastActiveAdminRole() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("last-admin");

        mockMvc.perform(put("/api/v1/employees/{employeeId}/roles", tenant.admin().userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRolesRequest(Set.of("EMPLOYEE")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LAST_ADMIN"));
    }

    @TestConfiguration
    static class EmployeeControllerIntegrationTestConfiguration {

        @Bean
        TestTenantFactory testTenantFactory(
                MockMvc mockMvc,
                ObjectMapper objectMapper,
                RegisterTenantUseCase registerTenantUseCase,
                UserRepository userRepository,
                PasswordHasher passwordHasher,
                Clock clock,
                IdGenerator idGenerator) {
            return new TestTenantFactory(
                    mockMvc, objectMapper, registerTenantUseCase, userRepository, passwordHasher, clock, idGenerator);
        }
    }
}
