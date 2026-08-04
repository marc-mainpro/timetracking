package com.tfp.timetracking.calendar.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.CalendarDayRule;
import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.DaySource;
import com.tfp.timetracking.calendar.domain.model.EffectiveCalendar;
import com.tfp.timetracking.calendar.domain.model.Holiday;
import com.tfp.timetracking.calendar.domain.model.SpecialDay;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato de resolucion por ambito (T70-02, RF-CAL-006): "la asignacion mas
 * especifica prevalece". Es la regla que reutilizaran turnos (T90) y ausencias
 * (T80), asi que se fija aqui caso por caso.
 */
class EffectiveCalendarResolverTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    private WorkCalendar calendar(String name, int expectedMinutes) {
        return calendar(name, expectedMinutes, LocalDate.of(2026, 1, 1), null, CalendarStatus.ACTIVE);
    }

    private WorkCalendar calendar(
            String name, int expectedMinutes, LocalDate validFrom, LocalDate validTo, CalendarStatus status) {
        return WorkCalendar.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                name,
                "Europe/Madrid",
                validFrom,
                validTo,
                status,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, expectedMinutes)),
                List.of(),
                List.of(),
                0L,
                NOW,
                NOW);
    }

    private CalendarAssignment assignment(WorkCalendar calendar, AssignmentScope scope, UUID targetId) {
        return CalendarAssignment.reconstitute(
                UUID.randomUUID(), TENANT_ID, calendar.id(), scope, targetId, NOW, NOW);
    }

    // --- Precedencia -----------------------------------------------------

    @Test
    void employeeAssignmentBeatsTeamAndTenant() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar teamCalendar = calendar("Equipo", 420);
        WorkCalendar employeeCalendar = calendar("Empleado", 360);

        Optional<EffectiveCalendar> resolved = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(teamCalendar, AssignmentScope.TEAM, TEAM_ID),
                        assignment(employeeCalendar, AssignmentScope.EMPLOYEE, EMPLOYEE_ID)),
                List.of(tenantCalendar, teamCalendar, employeeCalendar),
                EMPLOYEE_ID,
                TEAM_ID,
                MONDAY);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().calendar().name()).isEqualTo("Empleado");
        assertThat(resolved.get().scope()).isEqualTo(AssignmentScope.EMPLOYEE);
        assertThat(resolved.get().expectedHours()).isEqualTo(Duration.ofHours(6));
        assertThat(resolved.get().working()).isTrue();
        assertThat(resolved.get().date()).isEqualTo(MONDAY);
        assertThat(resolved.get().day().source()).isEqualTo(DaySource.WEEKLY_RULE);
    }

    @Test
    void teamAssignmentBeatsTenantWhenEmployeeHasNoneOfItsOwn() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar teamCalendar = calendar("Equipo", 420);

        Optional<EffectiveCalendar> resolved = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(teamCalendar, AssignmentScope.TEAM, TEAM_ID)),
                List.of(tenantCalendar, teamCalendar),
                EMPLOYEE_ID,
                TEAM_ID,
                MONDAY);

        assertThat(resolved).map(effective -> effective.calendar().name()).contains("Equipo");
    }

    @Test
    void fallsBackToTenantWhenTheCallerSuppliesNoTeam() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar teamCalendar = calendar("Equipo", 420);

        Optional<EffectiveCalendar> resolved = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(teamCalendar, AssignmentScope.TEAM, TEAM_ID)),
                List.of(tenantCalendar, teamCalendar),
                EMPLOYEE_ID,
                null,
                MONDAY);

        assertThat(resolved).map(effective -> effective.scope()).contains(AssignmentScope.TENANT);
    }

    @Test
    void ignoresAssignmentsTargetingOtherEmployeesOrTeams() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar otherCalendar = calendar("Otro", 60);

        Optional<EffectiveCalendar> resolved = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(otherCalendar, AssignmentScope.EMPLOYEE, UUID.randomUUID()),
                        assignment(otherCalendar, AssignmentScope.TEAM, UUID.randomUUID())),
                List.of(tenantCalendar, otherCalendar),
                EMPLOYEE_ID,
                TEAM_ID,
                MONDAY);

        assertThat(resolved).map(effective -> effective.scope()).contains(AssignmentScope.TENANT);
    }

    // --- Disponibilidad del calendario -----------------------------------

    @Test
    void archivedCalendarDoesNotBlockTheScopeAndResolutionFallsBack() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar archived =
                calendar("Archivado", 360, LocalDate.of(2026, 1, 1), null, CalendarStatus.ARCHIVED);

        Optional<EffectiveCalendar> resolved = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(archived, AssignmentScope.EMPLOYEE, EMPLOYEE_ID)),
                List.of(tenantCalendar, archived),
                EMPLOYEE_ID,
                TEAM_ID,
                MONDAY);

        assertThat(resolved).map(effective -> effective.calendar().name()).contains("Tenant");
    }

    @Test
    void calendarOutOfValidityForThatDateDoesNotWin() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar expired = calendar(
                "Caducado", 360, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), CalendarStatus.ACTIVE);

        Optional<EffectiveCalendar> resolved = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(expired, AssignmentScope.EMPLOYEE, EMPLOYEE_ID)),
                List.of(tenantCalendar, expired),
                EMPLOYEE_ID,
                TEAM_ID,
                MONDAY);

        assertThat(resolved).map(effective -> effective.calendar().name()).contains("Tenant");

        // El mismo empleado en una fecha cubierta por el calendario caducado si
        // resuelve al especifico: la vigencia se evalua fecha a fecha.
        Optional<EffectiveCalendar> inside = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(expired, AssignmentScope.EMPLOYEE, EMPLOYEE_ID)),
                List.of(tenantCalendar, expired),
                EMPLOYEE_ID,
                TEAM_ID,
                LocalDate.of(2025, 3, 3));
        assertThat(inside).map(effective -> effective.calendar().name()).contains("Caducado");
    }

    @Test
    void assignmentPointingToAnUnknownCalendarIsIgnored() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar missing = calendar("Ausente", 360);

        Optional<EffectiveCalendar> resolved = EffectiveCalendarResolver.resolve(
                List.of(
                        assignment(tenantCalendar, AssignmentScope.TENANT, null),
                        assignment(missing, AssignmentScope.EMPLOYEE, EMPLOYEE_ID)),
                List.of(tenantCalendar), // "missing" no se ha cargado
                EMPLOYEE_ID,
                TEAM_ID,
                MONDAY);

        assertThat(resolved).map(effective -> effective.scope()).contains(AssignmentScope.TENANT);
    }

    // --- Ausencia de resultado -------------------------------------------

    @Test
    void returnsEmptyWhenNothingApplies() {
        assertThat(EffectiveCalendarResolver.resolve(List.of(), List.of(), EMPLOYEE_ID, TEAM_ID, MONDAY))
                .isEmpty();
        assertThat(EffectiveCalendarResolver.resolve(null, null, EMPLOYEE_ID, TEAM_ID, MONDAY))
                .isEmpty();

        WorkCalendar other = calendar("Otro", 480);
        assertThat(EffectiveCalendarResolver.resolve(
                        List.of(assignment(other, AssignmentScope.EMPLOYEE, UUID.randomUUID())),
                        List.of(other),
                        EMPLOYEE_ID,
                        TEAM_ID,
                        MONDAY))
                .isEmpty();
    }

    @Test
    void rejectsNullDate() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> EffectiveCalendarResolver.resolve(
                        List.of(), List.of(), EMPLOYEE_ID, TEAM_ID, null));
    }

    // --- Desempate determinista ------------------------------------------

    @Test
    void breaksTiesWithinTheSameScopeByAssignmentIdSoResolutionIsDeterministic() {
        WorkCalendar first = calendar("Primero", 480);
        WorkCalendar second = calendar("Segundo", 300);
        UUID lowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

        CalendarAssignment low = CalendarAssignment.reconstitute(
                lowId, TENANT_ID, first.id(), AssignmentScope.EMPLOYEE, EMPLOYEE_ID, NOW, NOW);
        CalendarAssignment high = CalendarAssignment.reconstitute(
                highId, TENANT_ID, second.id(), AssignmentScope.EMPLOYEE, EMPLOYEE_ID, NOW, NOW);

        // Desempate lexicografico, no UUID.compareTo (que compara con signo y
        // ordenaria ffffffff... antes que 00000000...).
        assertThat(EffectiveCalendarResolver.resolve(
                        List.of(low, high), List.of(first, second), EMPLOYEE_ID, TEAM_ID, MONDAY))
                .map(effective -> effective.assignment().id())
                .contains(lowId);
        assertThat(EffectiveCalendarResolver.resolve(
                        List.of(high, low), List.of(first, second), EMPLOYEE_ID, TEAM_ID, MONDAY))
                .map(effective -> effective.assignment().id())
                .contains(lowId);
    }

    // --- mostSpecific -----------------------------------------------------

    @Test
    void mostSpecificIgnoresCalendarAvailability() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        WorkCalendar archived =
                calendar("Archivado", 360, LocalDate.of(2026, 1, 1), null, CalendarStatus.ARCHIVED);
        CalendarAssignment employeeAssignment = assignment(archived, AssignmentScope.EMPLOYEE, EMPLOYEE_ID);

        // A diferencia de resolve(), mostSpecific() solo mira el ambito: sirve
        // para responder "¿que asignacion rige a este empleado?".
        assertThat(EffectiveCalendarResolver.mostSpecific(
                        List.of(assignment(tenantCalendar, AssignmentScope.TENANT, null), employeeAssignment),
                        EMPLOYEE_ID,
                        TEAM_ID))
                .contains(employeeAssignment);

        assertThat(EffectiveCalendarResolver.mostSpecific(null, EMPLOYEE_ID, TEAM_ID)).isEmpty();
        assertThat(EffectiveCalendarResolver.mostSpecific(List.of(), EMPLOYEE_ID, TEAM_ID))
                .isEmpty();
    }

    // --- Interaccion con las reglas del calendario ganador ----------------

    @Test
    void appliesWinningCalendarOwnRulesIncludingHolidaysAndSpecialDays() {
        LocalDate holiday = LocalDate.of(2026, 3, 9); // lunes
        LocalDate special = LocalDate.of(2026, 3, 16); // lunes
        WorkCalendar employeeCalendar = WorkCalendar.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                "Empleado",
                "Europe/Madrid",
                LocalDate.of(2026, 1, 1),
                null,
                CalendarStatus.ACTIVE,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, 480)),
                List.of(new Holiday(holiday, "Festivo local")),
                List.of(new SpecialDay(special, "Intensiva", 300)),
                0L,
                NOW,
                NOW);
        List<CalendarAssignment> assignments =
                List.of(assignment(employeeCalendar, AssignmentScope.EMPLOYEE, EMPLOYEE_ID));

        assertThat(EffectiveCalendarResolver.resolve(
                        assignments, List.of(employeeCalendar), EMPLOYEE_ID, null, holiday))
                .map(EffectiveCalendar::working)
                .contains(false);
        assertThat(EffectiveCalendarResolver.resolve(
                        assignments, List.of(employeeCalendar), EMPLOYEE_ID, null, special))
                .map(EffectiveCalendar::expectedHours)
                .contains(Duration.ofMinutes(300));
    }

    @Test
    void effectiveCalendarRejectsNullComponents() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480);
        CalendarAssignment tenantAssignment = assignment(tenantCalendar, AssignmentScope.TENANT, null);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new EffectiveCalendar(null, tenantCalendar, tenantCalendar.dayOf(MONDAY)));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new EffectiveCalendar(tenantAssignment, null, tenantCalendar.dayOf(MONDAY)));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new EffectiveCalendar(tenantAssignment, tenantCalendar, null));
    }
}
