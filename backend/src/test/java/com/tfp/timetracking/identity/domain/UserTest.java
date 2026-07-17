package com.tfp.timetracking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.identity.domain.event.EmployeeCreated;
import com.tfp.timetracking.identity.domain.event.EmployeeDeactivated;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del agregado User (CONTEXT-DOMINIO §1, ficha T202):
 * validaciones de factoria, transiciones activate/deactivate/assignRoles y
 * eventos EmployeeCreated/EmployeeDeactivated.
 */
class UserTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final Clock fixedClock = () -> FIXED_NOW;

    @Test
    void createsActiveUserWithNormalizedEmailAndGeneratedId() {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        User user = User.create(
                TENANT_ID,
                "Jane.Doe@Example.COM",
                "hashed-password",
                "Jane",
                "Doe",
                Set.of(Role.EMPLOYEE),
                fixedClock,
                fixedIdGenerator(userId));

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.tenantId()).isEqualTo(TENANT_ID);
        assertThat(user.email().value()).isEqualTo("jane.doe@example.com");
        assertThat(user.passwordHash()).isEqualTo("hashed-password");
        assertThat(user.firstName()).isEqualTo("Jane");
        assertThat(user.lastName()).isEqualTo("Doe");
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
        assertThat(user.roles()).containsExactly(Role.EMPLOYEE);
        assertThat(user.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(user.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void createGeneratesEmployeeCreatedEventWithMinimalData() {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        User user = User.create(
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                Set.of(Role.EMPLOYEE, Role.TENANT_ADMIN),
                fixedClock,
                fixedIdGenerator(userId));

        List<Object> events = user.pullDomainEvents();

        assertThat(events).hasSize(1);
        EmployeeCreated event = (EmployeeCreated) events.get(0);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.aggregateId()).isEqualTo(userId);
        assertThat(event.email()).isEqualTo("jane@example.com");
        assertThat(event.roles()).containsExactlyInAnyOrder("EMPLOYEE", "TENANT_ADMIN");
        assertThat(event.occurredAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void rejectsInvalidEmailOnCreate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create(
                        TENANT_ID,
                        "not-an-email",
                        "hash",
                        "Jane",
                        "Doe",
                        Set.of(Role.EMPLOYEE),
                        fixedClock,
                        fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void rejectsEmptyRolesOnCreate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create(
                        TENANT_ID,
                        "jane@example.com",
                        "hash",
                        "Jane",
                        "Doe",
                        Set.of(),
                        fixedClock,
                        fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void rejectsNullRolesOnCreate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create(
                        TENANT_ID,
                        "jane@example.com",
                        "hash",
                        "Jane",
                        "Doe",
                        null,
                        fixedClock,
                        fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void rejectsBlankFirstName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create(
                        TENANT_ID,
                        "jane@example.com",
                        "hash",
                        "  ",
                        "Doe",
                        Set.of(Role.EMPLOYEE),
                        fixedClock,
                        fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void rejectsBlankLastName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create(
                        TENANT_ID,
                        "jane@example.com",
                        "hash",
                        "Jane",
                        "",
                        Set.of(Role.EMPLOYEE),
                        fixedClock,
                        fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void deactivateMarksUserInactiveAndGeneratesEvent() {
        UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        Instant createdAt = FIXED_NOW;
        Instant deactivatedAt = FIXED_NOW.plusSeconds(120);
        Deque<Instant> instants = new ArrayDeque<>(List.of(createdAt, deactivatedAt));
        Clock sequencedClock = instants::poll;

        User user = User.create(
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                Set.of(Role.EMPLOYEE),
                sequencedClock,
                fixedIdGenerator(userId));
        user.pullDomainEvents();

        user.deactivate(sequencedClock, fixedIdGenerator(UUID.randomUUID()));

        assertThat(user.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.isActive()).isFalse();
        assertThat(user.updatedAt()).isEqualTo(deactivatedAt);

        List<Object> events = user.pullDomainEvents();
        assertThat(events).hasSize(1);
        EmployeeDeactivated event = (EmployeeDeactivated) events.get(0);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.aggregateId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isEqualTo(deactivatedAt);
    }

    @Test
    void activateMarksUserActiveAgain() {
        User user = User.create(
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                Set.of(Role.EMPLOYEE),
                fixedClock,
                fixedIdGenerator(UUID.randomUUID()));
        user.deactivate(fixedClock, fixedIdGenerator(UUID.randomUUID()));

        user.activate(fixedClock);

        assertThat(user.isActive()).isTrue();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void assignRolesReplacesExistingRoles() {
        User user = User.create(
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                Set.of(Role.EMPLOYEE),
                fixedClock,
                fixedIdGenerator(UUID.randomUUID()));

        user.assignRoles(EnumSet.of(Role.TENANT_ADMIN), fixedClock);

        assertThat(user.roles()).containsExactly(Role.TENANT_ADMIN);
    }

    @Test
    void assignRolesRejectsEmptySet() {
        User user = User.create(
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                Set.of(Role.EMPLOYEE),
                fixedClock,
                fixedIdGenerator(UUID.randomUUID()));

        assertThatIllegalArgumentException().isThrownBy(() -> user.assignRoles(Set.of(), fixedClock));
    }

    @Test
    void reconstituteDoesNotGenerateDomainEvents() {
        User user = User.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                UserStatus.ACTIVE,
                Set.of(Role.EMPLOYEE),
                FIXED_NOW,
                FIXED_NOW);

        assertThat(user.pullDomainEvents()).isEmpty();
    }

    @Test
    void rolesAccessorReturnsDefensiveCopy() {
        User user = User.create(
                TENANT_ID,
                "jane@example.com",
                "hash",
                "Jane",
                "Doe",
                Set.of(Role.EMPLOYEE),
                fixedClock,
                fixedIdGenerator(UUID.randomUUID()));

        Set<Role> roles = user.roles();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> roles.add(Role.TENANT_ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static IdGenerator fixedIdGenerator(UUID firstId) {
        Deque<UUID> ids = new ArrayDeque<>();
        ids.add(firstId);
        return () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();
    }
}
