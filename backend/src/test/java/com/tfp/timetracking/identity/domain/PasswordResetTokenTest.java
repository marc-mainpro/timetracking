package com.tfp.timetracking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.identity.domain.event.PasswordResetRequested;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final UUID TOKEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = new IdGenerator() {
        private int count;

        @Override
        public UUID newId() {
            count++;
            return count == 1 ? TOKEN_ID : EVENT_ID;
        }
    };

    @Test
    void issueCreatesATokenAndDomainEvent() {
        User user = user();

        PasswordResetToken token = PasswordResetToken.issue(
                user,
                new GeneratedPasswordResetToken("raw-token", "hash-token"),
                Duration.ofHours(1),
                clock,
                idGenerator);

        assertThat(token.id()).isEqualTo(TOKEN_ID);
        assertThat(token.tenantId()).isEqualTo(user.tenantId());
        assertThat(token.userId()).isEqualTo(user.id());
        assertThat(token.tokenHash()).isEqualTo("hash-token");
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(token.pullDomainEvents()).singleElement().isInstanceOfSatisfying(PasswordResetRequested.class, event -> {
            assertThat(event.eventId()).isEqualTo(EVENT_ID);
            assertThat(event.email()).isEqualTo("jane@example.com");
            assertThat(event.resetToken()).isEqualTo("raw-token");
        });
    }

    @Test
    void consumeMarksTokenAsUsed() {
        PasswordResetToken token = PasswordResetToken.reconstitute(
                TOKEN_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hash-token",
                NOW.plusSeconds(60),
                null,
                NOW.minusSeconds(30));

        token.consume(NOW.plusSeconds(10));

        assertThat(token.usedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void consumeRejectsExpiredOrAlreadyUsedTokens() {
        PasswordResetToken expired = PasswordResetToken.reconstitute(
                TOKEN_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hash-token",
                NOW.minusSeconds(1),
                null,
                NOW.minusSeconds(30));
        PasswordResetToken used = PasswordResetToken.reconstitute(
                TOKEN_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hash-token",
                NOW.plusSeconds(60),
                NOW.minusSeconds(1),
                NOW.minusSeconds(30));

        assertThatThrownBy(() -> expired.consume(NOW)).isInstanceOf(InvalidPasswordResetTokenException.class);
        assertThatThrownBy(() -> used.consume(NOW)).isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    private User user() {
        return User.reconstitute(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE),
                NOW.minusSeconds(300),
                NOW.minusSeconds(300));
    }
}
