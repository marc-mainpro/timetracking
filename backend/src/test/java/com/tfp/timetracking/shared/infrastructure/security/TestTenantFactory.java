package com.tfp.timetracking.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.identity.interfaces.rest.AuthLoginRequest;
import com.tfp.timetracking.identity.interfaces.rest.AuthTokenResponse;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantRequest;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TestTenantFactory {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TestTenantFactory(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock,
            IdGenerator idGenerator) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public TenantActors createTenantActors(String seed) throws Exception {
        long suffix = Instant.now().toEpochMilli() + Math.abs(seed.hashCode());
        String clientIp = "198.51.100." + (Math.abs(seed.hashCode() % 200) + 20);
        String adminEmail = "admin+" + seed + "+" + suffix + "@acme.test";
        String adminPassword = "supersecretpwd";
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Tenant " + seed + " " + suffix,
                "Europe/Madrid",
                adminEmail,
                adminPassword,
                "Admin",
                seed);
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        RegisterTenantResponse response = objectMapper.readValue(responseBody, RegisterTenantResponse.class);

        String employeeEmail = "employee+" + seed + "+" + suffix + "@acme.test";
        String employeePassword = "employeepwd123";
        User employee = User.create(
                response.tenantId(),
                employeeEmail,
                passwordHasher.hash(employeePassword),
                "Employee",
                seed,
                Set.of(Role.EMPLOYEE),
                clock,
                idGenerator);
        userRepository.save(employee);
        employee.pullDomainEvents();

        String adminToken = login(adminEmail, adminPassword, clientIp + "-admin");
        String employeeToken = login(employeeEmail, employeePassword, clientIp + "-employee");

        return new TenantActors(
                response.tenantId(),
                new Actor(response.adminUserId(), adminEmail, adminPassword, adminToken, Set.of(Role.TENANT_ADMIN)),
                new Actor(employee.id(), employeeEmail, employeePassword, employeeToken, Set.of(Role.EMPLOYEE)));
    }

    private String login(String email, String password, String clientIp) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AuthTokenResponse.class).accessToken();
    }

    public record TenantActors(UUID tenantId, Actor admin, Actor employee) {}

    public record Actor(UUID userId, String email, String password, String token, Set<Role> roles) {}
}
