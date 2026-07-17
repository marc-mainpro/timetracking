package com.tfp.timetracking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TOKEN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = () -> TOKEN_ID;

    @Test
    void issuesTokenWithExpectedLifecycle() {
        RefreshToken refreshToken = RefreshToken.issue(USER_ID, "hash", NOW.plusSeconds(60), clock, idGenerator);

        assertThat(refreshToken.id()).isEqualTo(TOKEN_ID);
        assertThat(refreshToken.userId()).isEqualTo(USER_ID);
        assertThat(refreshToken.isRevoked()).isFalse();
        assertThat(refreshToken.isExpiredAt(NOW)).isFalse();
    }

    @Test
    void rotateRevokesCurrentTokenAndLinksReplacement() {
        RefreshToken refreshToken = RefreshToken.issue(USER_ID, "hash", NOW.plusSeconds(60), clock, idGenerator);
        UUID replacementId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        refreshToken.rotateTo(replacementId, NOW.plusSeconds(5));

        assertThat(refreshToken.isRevoked()).isTrue();
        assertThat(refreshToken.replacedBy()).isEqualTo(replacementId);
        assertThat(refreshToken.revokedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void rejectsNonFutureExpiry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RefreshToken.issue(USER_ID, "hash", NOW, clock, idGenerator));
    }
}
