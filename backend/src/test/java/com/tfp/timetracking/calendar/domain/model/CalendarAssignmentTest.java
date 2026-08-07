package com.tfp.timetracking.calendar.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.calendar.domain.event.CalendarAssigned;
import com.tfp.timetracking.calendar.domain.event.CalendarAssignmentRemoved;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas del agregado {@link CalendarAssignment} (T70-02, RF-CAL-006). */
class CalendarAssignmentTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CALENDAR_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = UUID::randomUUID;

    private CalendarAssignment assignment(AssignmentScope scope, UUID targetId) {
        return CalendarAssignment.create(TENANT_ID, CALENDAR_ID, scope, targetId, clock, idGenerator);
    }

    @Test
    void createsAssignmentAndEmitsAssignedEvent() {
        CalendarAssignment assignment = assignment(AssignmentScope.EMPLOYEE, EMPLOYEE_ID);

        assertThat(assignment.tenantId()).isEqualTo(TENANT_ID);
        assertThat(assignment.calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(assignment.scope()).isEqualTo(AssignmentScope.EMPLOYEE);
        assertThat(assignment.targetId()).isEqualTo(EMPLOYEE_ID);
        assertThat(assignment.createdAt()).isEqualTo(NOW);
        assertThat(assignment.updatedAt()).isEqualTo(NOW);

        List<Object> events = assignment.pullDomainEvents();
        assertThat(events).hasSize(1).first().isInstanceOf(CalendarAssigned.class);
        CalendarAssigned assigned = (CalendarAssigned) events.get(0);
        assertThat(assigned.scope()).isEqualTo(AssignmentScope.EMPLOYEE);
        assertThat(assigned.targetId()).isEqualTo(EMPLOYEE_ID);
        assertThat(assignment.pullDomainEvents()).isEmpty();
    }

    @Test
    void tenantScopeMustNotCarryTarget() {
        assertThat(assignment(AssignmentScope.TENANT, null).targetId()).isNull();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> assignment(AssignmentScope.TENANT, EMPLOYEE_ID))
                .withMessageContaining("no admite destinatario");
    }

    @Test
    void teamAndEmployeeScopesRequireTarget() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assignment(AssignmentScope.TEAM, null))
                .withMessageContaining("exige un destinatario");
        assertThatIllegalArgumentException().isThrownBy(() -> assignment(AssignmentScope.EMPLOYEE, null));
    }

    @Test
    void removeEmitsRemovedEventAndTouchesTimestamp() {
        CalendarAssignment assignment = assignment(AssignmentScope.TEAM, TEAM_ID);
        assignment.pullDomainEvents();

        assignment.remove(clock, idGenerator);

        List<Object> events = assignment.pullDomainEvents();
        assertThat(events).hasSize(1).first().isInstanceOf(CalendarAssignmentRemoved.class);
        CalendarAssignmentRemoved removed = (CalendarAssignmentRemoved) events.get(0);
        assertThat(removed.calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(removed.scope()).isEqualTo(AssignmentScope.TEAM);
        assertThat(removed.targetId()).isEqualTo(TEAM_ID);
        assertThat(assignment.updatedAt()).isEqualTo(NOW);
    }

    // --- appliesTo: la mitad "aplicabilidad" del contrato de resolucion ---

    @Test
    void tenantScopeAppliesToEveryEmployee() {
        CalendarAssignment tenantWide = assignment(AssignmentScope.TENANT, null);

        assertThat(tenantWide.appliesTo(EMPLOYEE_ID, TEAM_ID)).isTrue();
        assertThat(tenantWide.appliesTo(UUID.randomUUID(), null)).isTrue();
        assertThat(tenantWide.appliesTo(null, null)).isTrue();
    }

    @Test
    void employeeScopeAppliesOnlyToThatEmployee() {
        CalendarAssignment forEmployee = assignment(AssignmentScope.EMPLOYEE, EMPLOYEE_ID);

        assertThat(forEmployee.appliesTo(EMPLOYEE_ID, null)).isTrue();
        assertThat(forEmployee.appliesTo(UUID.randomUUID(), null)).isFalse();
        assertThat(forEmployee.appliesTo(null, null)).isFalse();
    }

    @Test
    void teamScopeNeedsTheCallerToSupplyTheTeam() {
        CalendarAssignment forTeam = assignment(AssignmentScope.TEAM, TEAM_ID);

        assertThat(forTeam.appliesTo(EMPLOYEE_ID, TEAM_ID)).isTrue();
        assertThat(forTeam.appliesTo(EMPLOYEE_ID, UUID.randomUUID())).isFalse();
        // Un empleado sin equipo conocido nunca casa con una asignacion de equipo.
        assertThat(forTeam.appliesTo(EMPLOYEE_ID, null)).isFalse();
    }

    @Test
    void reconstituteRestoresStateWithoutEvents() {
        UUID id = UUID.randomUUID();
        CalendarAssignment assignment = CalendarAssignment.reconstitute(
                id, TENANT_ID, CALENDAR_ID, AssignmentScope.TEAM, TEAM_ID, NOW, NOW);

        assertThat(assignment.id()).isEqualTo(id);
        assertThat(assignment.pullDomainEvents()).isEmpty();
    }

    @Test
    void rejectsNullMandatoryArguments() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CalendarAssignment.create(
                        null, CALENDAR_ID, AssignmentScope.TENANT, null, clock, idGenerator));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CalendarAssignment.create(
                        TENANT_ID, null, AssignmentScope.TENANT, null, clock, idGenerator));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CalendarAssignment.create(TENANT_ID, CALENDAR_ID, null, null, clock, idGenerator));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CalendarAssignment.create(
                        TENANT_ID, CALENDAR_ID, AssignmentScope.TENANT, null, null, idGenerator));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CalendarAssignment.reconstitute(
                        null, TENANT_ID, CALENDAR_ID, AssignmentScope.TENANT, null, NOW, NOW));
        CalendarAssignment assignment = assignment(AssignmentScope.TENANT, null);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> assignment.remove(null, idGenerator));
    }
}
