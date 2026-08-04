package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.VerificationToken;
import com.tfp.timetracking.tenant.domain.VerificationTokenGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Utilidades compartidas por los tests unitarios de los casos de uso de T53. */
final class TenantRegistrationTestFixtures {

    static final Instant NOW = Instant.parse("2026-02-01T09:00:00Z");
    static final Clock CLOCK = () -> NOW;
    static final IdGenerator ID_GENERATOR = UUID::randomUUID;

    private TenantRegistrationTestFixtures() {}

    static RegistrationProperties properties() {
        return new RegistrationProperties(
                new RegistrationProperties.Verification(Duration.ofHours(24), 3),
                new RegistrationProperties.Throttle(Duration.ofHours(1), 5, 3),
                "https://app.test/registro/verificar?token=%s",
                "pepper");
    }

    /** Generador determinista: token-1, token-2… con hash en Base64. */
    static final class StubTokenGenerator implements VerificationTokenGenerator {
        private final AtomicInteger counter = new AtomicInteger();
        private String lastValue;

        @Override
        public VerificationToken generate() {
            lastValue = "token-" + counter.incrementAndGet();
            return new VerificationToken(lastValue, hash(lastValue));
        }

        @Override
        public String hash(String rawToken) {
            return Base64.getEncoder().encodeToString(rawToken.getBytes(StandardCharsets.UTF_8));
        }

        String lastValue() {
            return lastValue;
        }
    }

    static TenantRegistration pendingRegistration(VerificationTokenGenerator tokenGenerator) {
        return TenantRegistration.request(
                "Acme Corp",
                "Jane",
                "Doe",
                "owner@acme.test",
                "hashed-password",
                "Europe/Madrid",
                "PUBLIC_WEB",
                "ip-hash",
                Duration.ofHours(24),
                CLOCK,
                ID_GENERATOR,
                tokenGenerator);
    }

    static TenantRegistration verifiedRegistration(StubTokenGenerator tokenGenerator) {
        TenantRegistration registration = pendingRegistration(tokenGenerator);
        registration.verifyEmail(tokenGenerator.lastValue(), tokenGenerator, CLOCK, ID_GENERATOR);
        registration.pullDomainEvents();
        return registration;
    }
}
