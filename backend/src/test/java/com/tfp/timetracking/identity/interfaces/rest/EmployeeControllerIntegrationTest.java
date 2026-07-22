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
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));

        mockMvc.perform(get("/api/v1/employees")
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
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
                UserRepository userRepository,
                PasswordHasher passwordHasher,
                Clock clock,
                IdGenerator idGenerator) {
            return new TestTenantFactory(mockMvc, objectMapper, userRepository, passwordHasher, clock, idGenerator);
        }
    }
}
