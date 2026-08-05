package com.tfp.timetracking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = () -> SESSION_ID;

    @Test
    void startsSessionWithExpectedLifecycle() {
        Session session = Session.start(USER_ID, TENANT_ID, NOW.plusSeconds(60), clock, idGenerator);

        assertThat(session.id()).isEqualTo(SESSION_ID);
        assertThat(session.userId()).isEqualTo(USER_ID);
        assertThat(session.tenantId()).isEqualTo(TENANT_ID);
        assertThat(session.createdAt()).isEqualTo(NOW);
        assertThat(session.lastUsedAt()).isEqualTo(NOW);
        assertThat(session.isActiveAt(NOW)).isTrue();
    }

    @Test
    void touchExtendsExpiryAndUpdatesLastUse() {
        Session session = Session.start(USER_ID, TENANT_ID, NOW.plusSeconds(60), clock, idGenerator);

        session.touch(NOW.plusSeconds(15), NOW.plusSeconds(120));

        assertThat(session.lastUsedAt()).isEqualTo(NOW.plusSeconds(15));
        assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void revokeMakesSessionInactive() {
        Session session = Session.start(USER_ID, TENANT_ID, NOW.plusSeconds(60), clock, idGenerator);

        session.revoke(NOW.plusSeconds(5));

        assertThat(session.isRevoked()).isTrue();
        assertThat(session.isActiveAt(NOW.plusSeconds(6))).isFalse();
    }

    @Test
    void rejectsNonFutureExpiry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Session.start(USER_ID, TENANT_ID, NOW, clock, idGenerator));
    }
}
