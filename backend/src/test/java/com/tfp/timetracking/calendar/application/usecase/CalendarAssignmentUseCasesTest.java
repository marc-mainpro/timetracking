package com.tfp.timetracking.calendar.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.calendar.application.command.AssignCalendarCommand;
import com.tfp.timetracking.calendar.domain.event.CalendarAssigned;
import com.tfp.timetracking.calendar.domain.event.CalendarAssignmentRemoved;
import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarArchivedException;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignmentAlreadyExistsException;
import com.tfp.timetracking.calendar.domain.model.CalendarDayRule;
import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.DaySource;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.CalendarAssignmentRepository;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.EmployeeAssignmentTargetQuery;
import com.tfp.timetracking.shared.application.EmployeeAssignmentTargetQuery.TargetStatus;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Casos de uso de asignacion y de resolucion del calendario efectivo (T70-02/04). */
class CalendarAssignmentUseCasesTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    private final CalendarAssignmentRepository assignmentRepository = mock(CalendarAssignmentRepository.class);
    private final WorkCalendarRepository calendarRepository = mock(WorkCalendarRepository.class);
    private final EmployeeAssignmentTargetQuery targetQuery = mock(EmployeeAssignmentTargetQuery.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = UUID::randomUUID;

    private final TenantContext tenantContext = new TenantContext() {
        @Override
        public UUID currentTenantId() {
            return TENANT_ID;
        }

        @Override
        public UUID currentUserId() {
            return UUID.randomUUID();
        }

        @Override
        public Set<String> currentRoles() {
            return Set.of("TENANT_ADMIN");
        }
    };

    private AssignCalendarUseCase assignUseCase;
    private RemoveCalendarAssignmentUseCase removeUseCase;
    private ListCalendarAssignmentsUseCase listUseCase;
    private ResolveEffectiveCalendarUseCase resolveUseCase;

    @BeforeEach
    void setUp() {
        // Por defecto el destinatario es un empleado: los casos que prueban
        // otra cosa lo redefinen.
        org.mockito.Mockito.when(targetQuery.check(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(TargetStatus.ASSIGNABLE);
        assignUseCase = new AssignCalendarUseCase(
                assignmentRepository,
                calendarRepository,
                targetQuery,
                tenantContext,
                domainEventPublisher,
                auditRecorder,
                clock,
                idGenerator);
        removeUseCase = new RemoveCalendarAssignmentUseCase(
                assignmentRepository, tenantContext, domainEventPublisher, auditRecorder, clock, idGenerator);
        listUseCase = new ListCalendarAssignmentsUseCase(assignmentRepository, tenantContext);
        resolveUseCase = new ResolveEffectiveCalendarUseCase(assignmentRepository, calendarRepository, tenantContext);
        when(assignmentRepository.save(any(CalendarAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private WorkCalendar calendar(String name, int minutes, CalendarStatus status) {
        return WorkCalendar.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                name,
                "Europe/Madrid",
                LocalDate.of(2026, 1, 1),
                null,
                status,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, minutes)),
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

    // --- Asignar ----------------------------------------------------------

    @Test
    void assignPersistsPublishesEventAndAuditsWithTarget() {
        WorkCalendar calendar = calendar("General", 480, CalendarStatus.ACTIVE);
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));
        when(assignmentRepository.findByScope(TENANT_ID, AssignmentScope.EMPLOYEE, EMPLOYEE_ID))
                .thenReturn(Optional.empty());

        CalendarAssignment saved = assignUseCase.assign(
                new AssignCalendarCommand(calendar.id(), AssignmentScope.EMPLOYEE, EMPLOYEE_ID));

        assertThat(saved.tenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.scope()).isEqualTo(AssignmentScope.EMPLOYEE);

        ArgumentCaptor<List<Object>> events = ArgumentCaptor.forClass(List.class);
        verify(domainEventPublisher).publish(events.capture());
        assertThat(events.getValue()).hasSize(1).first().isInstanceOf(CalendarAssigned.class);

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder)
                .record(eq("CALENDAR_ASSIGNED"), eq("CalendarAssignment"), eq(saved.id()), metadata.capture());
        assertThat(metadata.getValue())
                .containsEntry("scope", "EMPLOYEE")
                .containsEntry("targetId", EMPLOYEE_ID.toString());
    }

    @Test
    void assignOmitsTargetFromAuditMetadataForTenantScope() {
        WorkCalendar calendar = calendar("General", 480, CalendarStatus.ACTIVE);
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));
        when(assignmentRepository.findByScope(TENANT_ID, AssignmentScope.TENANT, null))
                .thenReturn(Optional.empty());

        CalendarAssignment saved =
                assignUseCase.assign(new AssignCalendarCommand(calendar.id(), AssignmentScope.TENANT, null));

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder).record(any(), any(), eq(saved.id()), metadata.capture());
        assertThat(metadata.getValue()).doesNotContainKey("targetId");
    }

    @Test
    void assignReturns404WhenCalendarIsUnknownOrFromAnotherTenant() {
        UUID foreignCalendarId = UUID.randomUUID();
        when(calendarRepository.findById(TENANT_ID, foreignCalendarId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> assignUseCase.assign(
                        new AssignCalendarCommand(foreignCalendarId, AssignmentScope.EMPLOYEE, EMPLOYEE_ID)));
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    void assignRejectsArchivedCalendar() {
        WorkCalendar archived = calendar("Archivado", 480, CalendarStatus.ARCHIVED);
        when(calendarRepository.findById(TENANT_ID, archived.id())).thenReturn(Optional.of(archived));

        assertThatExceptionOfType(CalendarArchivedException.class)
                .isThrownBy(() -> assignUseCase.assign(
                        new AssignCalendarCommand(archived.id(), AssignmentScope.TENANT, null)));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsEmployeeScopeForSomeoneWithoutTheEmployeeRole() {
        WorkCalendar calendar = calendar("General", 480, CalendarStatus.ACTIVE);
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));
        when(targetQuery.check(TENANT_ID, EMPLOYEE_ID)).thenReturn(TargetStatus.NOT_EMPLOYEE);

        assertThatExceptionOfType(com.tfp.timetracking.shared.domain.TargetNotEmployeeException.class)
                .isThrownBy(() -> assignUseCase.assign(
                        new AssignCalendarCommand(calendar.id(), AssignmentScope.EMPLOYEE, EMPLOYEE_ID)));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsEmployeeScopeForAnUnknownTarget() {
        WorkCalendar calendar = calendar("General", 480, CalendarStatus.ACTIVE);
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));
        when(targetQuery.check(TENANT_ID, EMPLOYEE_ID)).thenReturn(TargetStatus.UNKNOWN);

        assertThatExceptionOfType(com.tfp.timetracking.shared.application.ResourceNotFoundException.class)
                .isThrownBy(() -> assignUseCase.assign(
                        new AssignCalendarCommand(calendar.id(), AssignmentScope.EMPLOYEE, EMPLOYEE_ID)));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assignDoesNotCheckTheTargetForTenantScope() {
        WorkCalendar calendar = calendar("General", 480, CalendarStatus.ACTIVE);
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));

        assignUseCase.assign(new AssignCalendarCommand(calendar.id(), AssignmentScope.TENANT, null));

        verify(targetQuery, never()).check(any(), any());
    }

    @Test
    void assignRejectsDuplicateScope() {
        WorkCalendar calendar = calendar("General", 480, CalendarStatus.ACTIVE);
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));
        when(assignmentRepository.findByScope(TENANT_ID, AssignmentScope.TEAM, TEAM_ID))
                .thenReturn(Optional.of(assignment(calendar, AssignmentScope.TEAM, TEAM_ID)));

        assertThatExceptionOfType(CalendarAssignmentAlreadyExistsException.class)
                .isThrownBy(() -> assignUseCase.assign(
                        new AssignCalendarCommand(calendar.id(), AssignmentScope.TEAM, TEAM_ID)))
                .matches(ex -> ex.errorCode().equals("CALENDAR_ASSIGNMENT_ALREADY_EXISTS"));
        verify(assignmentRepository, never()).save(any());
    }

    // --- Retirar ----------------------------------------------------------

    @Test
    void removeDeletesTenantScopedAndPublishesRemovedEvent() {
        WorkCalendar calendar = calendar("General", 480, CalendarStatus.ACTIVE);
        CalendarAssignment existing = assignment(calendar, AssignmentScope.TEAM, TEAM_ID);
        when(assignmentRepository.findById(TENANT_ID, existing.id())).thenReturn(Optional.of(existing));

        removeUseCase.remove(existing.id());

        verify(assignmentRepository).delete(TENANT_ID, existing.id());
        ArgumentCaptor<List<Object>> events = ArgumentCaptor.forClass(List.class);
        verify(domainEventPublisher).publish(events.capture());
        assertThat(events.getValue()).hasSize(1).first().isInstanceOf(CalendarAssignmentRemoved.class);
        verify(auditRecorder)
                .record(eq("CALENDAR_ASSIGNMENT_REMOVED"), eq("CalendarAssignment"), eq(existing.id()), any());
    }

    @Test
    void removeReturns404ForForeignAssignment() {
        UUID foreign = UUID.randomUUID();
        when(assignmentRepository.findById(TENANT_ID, foreign)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> removeUseCase.remove(foreign));
        verify(assignmentRepository, never()).delete(any(), any());
    }

    @Test
    void listDelegatesTenantScopedPagedQuery() {
        PagedResult<CalendarAssignment> page = new PagedResult<>(List.of(), 0, 20, 0, 0);
        when(assignmentRepository.findByTenant(TENANT_ID, null, 0, 20)).thenReturn(page);

        assertThat(listUseCase.list(null, 0, 20)).isSameAs(page);
    }

    // --- Resolver el calendario efectivo -----------------------------------

    @Test
    void resolveAppliesScopePrecedenceOverTheLoadedCalendars() {
        WorkCalendar tenantCalendar = calendar("Tenant", 480, CalendarStatus.ACTIVE);
        WorkCalendar employeeCalendar = calendar("Empleado", 300, CalendarStatus.ACTIVE);
        List<CalendarAssignment> assignments = List.of(
                assignment(tenantCalendar, AssignmentScope.TENANT, null),
                assignment(employeeCalendar, AssignmentScope.EMPLOYEE, EMPLOYEE_ID));
        when(assignmentRepository.findApplicable(TENANT_ID, EMPLOYEE_ID, TEAM_ID)).thenReturn(assignments);
        when(calendarRepository.findAllByIds(eq(TENANT_ID), any()))
                .thenReturn(List.of(tenantCalendar, employeeCalendar));

        assertThat(resolveUseCase.resolve(EMPLOYEE_ID, TEAM_ID, MONDAY))
                .map(effective -> effective.calendar().name())
                .contains("Empleado");
        assertThat(resolveUseCase.resolve(EMPLOYEE_ID, TEAM_ID, MONDAY))
                .map(effective -> effective.expectedHours())
                .contains(Duration.ofMinutes(300));
    }

    @Test
    void resolveShortCircuitsWhenThereAreNoAssignments() {
        when(assignmentRepository.findApplicable(TENANT_ID, EMPLOYEE_ID, null)).thenReturn(List.of());

        assertThat(resolveUseCase.resolve(EMPLOYEE_ID, null, MONDAY)).isEmpty();
        // No merece la pena consultar calendarios si no hay ninguna asignacion.
        verify(calendarRepository, never()).findAllByIds(any(), any());
    }

    @Test
    void resolveViewFlattensTheResultForTheApiBoundary() {
        WorkCalendar employeeCalendar = calendar("Empleado", 300, CalendarStatus.ACTIVE);
        CalendarAssignment employeeAssignment =
                assignment(employeeCalendar, AssignmentScope.EMPLOYEE, EMPLOYEE_ID);
        when(assignmentRepository.findApplicable(TENANT_ID, EMPLOYEE_ID, null))
                .thenReturn(List.of(employeeAssignment));
        when(calendarRepository.findAllByIds(eq(TENANT_ID), any())).thenReturn(List.of(employeeCalendar));

        assertThat(resolveUseCase.resolveView(EMPLOYEE_ID, null, MONDAY))
                .hasValueSatisfying(view -> {
                    assertThat(view.calendarId()).isEqualTo(employeeCalendar.id());
                    assertThat(view.calendarName()).isEqualTo("Empleado");
                    assertThat(view.timezone()).isEqualTo("Europe/Madrid");
                    assertThat(view.assignmentId()).isEqualTo(employeeAssignment.id());
                    assertThat(view.scope()).isEqualTo("EMPLOYEE");
                    assertThat(view.targetId()).isEqualTo(EMPLOYEE_ID);
                    assertThat(view.date()).isEqualTo(MONDAY);
                    assertThat(view.working()).isTrue();
                    assertThat(view.expectedMinutes()).isEqualTo(300);
                    assertThat(view.source()).isEqualTo(DaySource.WEEKLY_RULE);
                });
    }

    @Test
    void resolveRejectsNullEmployeeOrDate() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> resolveUseCase.resolve(null, TEAM_ID, MONDAY));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> resolveUseCase.resolve(EMPLOYEE_ID, TEAM_ID, null));
    }
}
