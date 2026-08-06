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
import com.tfp.timetracking.tenant.application.RegisterTenantCommand;
import com.tfp.timetracking.tenant.application.RegisterTenantResult;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Levanta un tenant con sus actores para los tests de integracion.
 *
 * <p>Crea el tenant invocando {@link RegisterTenantUseCase}, el mismo caso de
 * uso que usa la creacion desde plataforma, en lugar de llamar por HTTP a un
 * endpoint publico de alta. Cuando existia {@code POST /api/v1/auth/register},
 * este helper era su principal consumidor y lo mantenia vivo pese a que creaba
 * tenants ACTIVE saltandose el flujo de aprobacion de la V2.
 *
 * <p>El login si va por HTTP: los tests necesitan JWT reales emitidos por la
 * cadena de seguridad, no tokens fabricados.
 */
public class TestTenantFactory {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final RegisterTenantUseCase registerTenantUseCase;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TestTenantFactory(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            RegisterTenantUseCase registerTenantUseCase,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock,
            IdGenerator idGenerator) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.registerTenantUseCase = registerTenantUseCase;
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
        RegisterTenantResult response = registerTenantUseCase.register(new RegisterTenantCommand(
                "Tenant " + seed + " " + suffix,
                "Europe/Madrid",
                adminEmail,
                adminPassword,
                "Admin",
                seed));

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
