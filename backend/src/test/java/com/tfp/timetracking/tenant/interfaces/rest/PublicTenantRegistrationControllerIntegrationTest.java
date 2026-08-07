package com.tfp.timetracking.tenant.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.outbox.application.PublishPendingOutboxMessages;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flujo público de alta (T53-03/T53-05): solicitud → correo de verificación por
 * Outbox → confirmación. Verifica además que la respuesta es indistinguible
 * para un correo existente y uno inexistente (RF-REG-005) y que ningún camino
 * crea un tenant.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "registration.public.enabled=true",
            "registration.throttle.max-per-ip=3",
            "registration.throttle.max-per-email=2",
            "registration.verification-url-template=https://app.test/registro/verificar?token=%s"
        })
class PublicTenantRegistrationControllerIntegrationTest {

    private static final String ENDPOINT = "/api/v1/public/tenant-registrations";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

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
    private PublishPendingOutboxMessages publishPendingOutboxMessages;

    @Autowired
    private TenantRegistrationRepository registrationRepository;

    @Autowired
    private CapturingEmailSender emailSender;

    @Autowired
    private TestTenantFactory testTenantFactory;

    @BeforeEach
    void clearCapturedEmails() {
        emailSender.clear();
    }

    private String uniqueEmail(String seed) {
        return "owner+" + seed + "+" + Instant.now().toEpochMilli() + "@acme.test";
    }

    private TenantRegistrationRequestBody body(String email) {
        return new TenantRegistrationRequestBody(
                "Acme " + email, "Europe/Madrid", "Jane", "Doe", email, "supersecretpwd", true);
    }

    private void submit(String email, String clientIp) throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(email))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Entrega los mensajes del outbox y devuelve el token del último correo enviado. */
    private String deliverAndReadToken() {
        publishPendingOutboxMessages.publishBatch();
        List<EmailMessage> messages = emailSender.captured();
        assertThat(messages).isNotEmpty();
        Matcher matcher = TOKEN_IN_LINK.matcher(messages.get(messages.size() - 1).body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void requestThenVerifyMovesTheRegistrationToReviewWithoutCreatingATenant() throws Exception {
        String email = uniqueEmail("happy");

        submit(email, "203.0.113.21");

        assertThat(registrationRepository.findOpenByEmail(email))
                .get()
                .satisfies(registration -> {
                    assertThat(registration.status())
                            .isEqualTo(TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION);
                    // Ni tenant ni contraseña en claro: solo la solicitud.
                    assertThat(registration.createdTenantId()).isNull();
                    assertThat(registration.ownerPasswordHash()).isNotEqualTo("supersecretpwd");
                });

        String token = deliverAndReadToken();

        mockMvc.perform(post(ENDPOINT + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationEmailRequest(token))))
                .andExpect(status().isOk());

        assertThat(registrationRepository.findOpenByEmail(email))
                .get()
                .satisfies(registration -> {
                    assertThat(registration.status()).isEqualTo(TenantRegistrationStatus.PENDING_REVIEW);
                    assertThat(registration.createdTenantId()).isNull();
                });
    }

    @Test
    void theResponseIsIdenticalForAnUnknownEmailAndForOneThatAlreadyHasAnAccount() throws Exception {
        String fresh = uniqueEmail("fresh");
        // Correo con cuenta real ya creada (usuario existente en un tenant).
        String taken = testTenantFactory.createTenantActors("enum").admin().email();

        String freshResponse = mockMvc.perform(post(ENDPOINT)
                        .header("X-Forwarded-For", "203.0.113.23")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(fresh))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String takenResponse = mockMvc.perform(post(ENDPOINT)
                        .header("X-Forwarded-For", "203.0.113.24")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(taken))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(takenResponse).isEqualTo(freshResponse);
        // …y, pese a la respuesta idéntica, no se ha creado ninguna solicitud
        // para el correo que ya tenía cuenta.
        assertThat(registrationRepository.findOpenByEmail(taken)).isEmpty();
        assertThat(registrationRepository.findOpenByEmail(fresh)).isPresent();
    }

    @Test
    void theResendResponseIsIdenticalForAKnownAndAnUnknownEmail() throws Exception {
        String known = uniqueEmail("known-resend");
        submit(known, "203.0.113.25");

        String knownResponse = resend(known);
        String unknownResponse = resend("nadie+" + Instant.now().toEpochMilli() + "@acme.test");

        assertThat(knownResponse).isEqualTo(unknownResponse);
    }

    private String resend(String email) throws Exception {
        return mockMvc.perform(post(ENDPOINT + "/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendVerificationRequest(email))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void aTokenCanBeUsedOnlyOnce() throws Exception {
        submit(uniqueEmail("single-use"), "203.0.113.26");
        String token = deliverAndReadToken();

        mockMvc.perform(post(ENDPOINT + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationEmailRequest(token))))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationEmailRequest(token))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void anInventedTokenIsRejectedWithTheSameErrorAsAConsumedOne() throws Exception {
        mockMvc.perform(post(ENDPOINT + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyRegistrationEmailRequest("token-que-no-existe"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void resendingIssuesANewTokenAndInvalidatesThePreviousOne() throws Exception {
        String email = uniqueEmail("resend-rotates");
        submit(email, "203.0.113.27");
        String firstToken = deliverAndReadToken();

        resend(email);
        String secondToken = deliverAndReadToken();
        assertThat(secondToken).isNotEqualTo(firstToken);

        mockMvc.perform(post(ENDPOINT + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationEmailRequest(firstToken))))
                .andExpect(status().isConflict());

        mockMvc.perform(post(ENDPOINT + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRegistrationEmailRequest(secondToken))))
                .andExpect(status().isOk());
    }

    @Test
    void requestsFromTheSameIpStopBeingStoredOnceTheQuotaIsSpent() throws Exception {
        String ip = "203.0.113.99";
        for (int i = 0; i < 3; i++) {
            submit(uniqueEmail("quota-" + i), ip);
        }
        String blocked = uniqueEmail("quota-blocked");

        // La cuarta responde igual que las anteriores (RF-REG-005)…
        submit(blocked, ip);

        // …pero no ha creado ninguna solicitud.
        assertThat(registrationRepository.findOpenByEmail(blocked)).isEmpty();
    }

    @Test
    void validationErrorsAreReportedFieldByField() throws Exception {
        TenantRegistrationRequestBody invalid =
                new TenantRegistrationRequestBody(" ", "Europe/Madrid", "Jane", "Doe", "no-arroba", "corta", false);

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Forwarded-For", "203.0.113.28")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'companyName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void acceptingTheTermsIsMandatory() throws Exception {
        TenantRegistrationRequestBody withoutTerms = new TenantRegistrationRequestBody(
                "Acme", "Europe/Madrid", "Jane", "Doe", uniqueEmail("terms"), "supersecretpwd", false);

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Forwarded-For", "203.0.113.29")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutTerms)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'termsAccepted')]").exists());
    }

    @Test
    void theVerificationEmailNeverLeaksThePasswordAndCarriesTheLink() throws Exception {
        submit(uniqueEmail("email-content"), "203.0.113.30");
        publishPendingOutboxMessages.publishBatch();

        assertThat(emailSender.captured()).isNotEmpty();
        EmailMessage message = emailSender.captured().get(emailSender.captured().size() - 1);
        assertThat(message.body()).contains("https://app.test/registro/verificar?token=");
        assertThat(message.body()).doesNotContain("supersecretpwd");
    }

    /** Doble de test del puerto {@link EmailSender}: sin él, el token se perdería (ADR-0012). */
    static final class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            messages.add(message);
        }

        List<EmailMessage> captured() {
            return List.copyOf(messages);
        }

        void clear() {
            messages.clear();
        }
    }

    @TestConfiguration
    static class PublicRegistrationTestConfiguration {

        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }

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
