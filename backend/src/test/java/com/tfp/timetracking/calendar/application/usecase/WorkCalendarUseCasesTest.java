package com.tfp.timetracking.calendar.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.calendar.application.CalendarProperties;
import com.tfp.timetracking.calendar.application.command.DayRuleCommand;
import com.tfp.timetracking.calendar.application.command.HolidayCommand;
import com.tfp.timetracking.calendar.application.command.SaveWorkCalendarCommand;
import com.tfp.timetracking.calendar.application.command.SpecialDayCommand;
import com.tfp.timetracking.calendar.domain.event.WorkCalendarCreated;
import com.tfp.timetracking.calendar.domain.model.CalendarArchivedException;
import com.tfp.timetracking.calendar.domain.model.CalendarDayRule;
import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.DuplicateCalendarNameException;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.DayOfWeek;
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

/**
 * Casos de uso de gestion de calendarios (T70-04): crear, editar, archivar,
 * consultar y listar.
 */
class WorkCalendarUseCasesTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    private final WorkCalendarRepository calendarRepository = mock(WorkCalendarRepository.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final Clock clock = () -> NOW;
    private final IdGenerator idGenerator = UUID::randomUUID;
    private final TenantContext tenantContext = tenantContext(TENANT_ID);

    private final CalendarCommandTranslator translator =
            new CalendarCommandTranslator(new CalendarProperties(400, 400, "Europe/Madrid"));

    private CreateWorkCalendarUseCase createUseCase;
    private UpdateWorkCalendarUseCase updateUseCase;
    private ArchiveWorkCalendarUseCase archiveUseCase;
    private GetWorkCalendarUseCase getUseCase;
    private ListWorkCalendarsUseCase listUseCase;

    private static TenantContext tenantContext(UUID tenantId) {
        return new TenantContext() {
            @Override
            public UUID currentTenantId() {
                return tenantId;
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
    }

    @BeforeEach
    void setUp() {
        createUseCase = new CreateWorkCalendarUseCase(
                calendarRepository, translator, tenantContext, domainEventPublisher, auditRecorder, clock, idGenerator);
        updateUseCase = new UpdateWorkCalendarUseCase(
                calendarRepository, translator, tenantContext, domainEventPublisher, auditRecorder, clock, idGenerator);
        archiveUseCase = new ArchiveWorkCalendarUseCase(
                calendarRepository, tenantContext, domainEventPublisher, auditRecorder, clock, idGenerator);
        getUseCase = new GetWorkCalendarUseCase(calendarRepository, tenantContext);
        listUseCase = new ListWorkCalendarsUseCase(calendarRepository, tenantContext);
        when(calendarRepository.save(any(WorkCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SaveWorkCalendarCommand command(String name) {
        return new SaveWorkCalendarCommand(
                name,
                "Europe/Madrid",
                FROM,
                null,
                List.of(new DayRuleCommand(DayOfWeek.MONDAY, true, 480)),
                List.of(new HolidayCommand(LocalDate.of(2026, 1, 6), "Reyes")),
                List.of(new SpecialDayCommand(LocalDate.of(2026, 12, 24), "Intensiva", 300)));
    }

    private WorkCalendar existingCalendar() {
        return WorkCalendar.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                "General",
                "Europe/Madrid",
                FROM,
                null,
                CalendarStatus.ACTIVE,
                List.of(CalendarDayRule.working(DayOfWeek.MONDAY, 480)),
                List.of(),
                List.of(),
                3L,
                NOW,
                NOW);
    }

    // --- Crear ------------------------------------------------------------

    @Test
    void createPersistsPublishesEventsAndAudits() {
        WorkCalendar created = createUseCase.create(command("General"));

        assertThat(created.tenantId()).isEqualTo(TENANT_ID);
        assertThat(created.name()).isEqualTo("General");
        assertThat(created.holidays()).hasSize(1);
        assertThat(created.specialDays()).hasSize(1);

        ArgumentCaptor<List<Object>> events = ArgumentCaptor.forClass(List.class);
        verify(domainEventPublisher).publish(events.capture());
        assertThat(events.getValue()).hasSize(1).first().isInstanceOf(WorkCalendarCreated.class);

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder)
                .record(eq("CALENDAR_CREATED"), eq("WorkCalendar"), eq(created.id()), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("name", "General").containsEntry("timezone", "Europe/Madrid");
    }

    @Test
    void createTakesTenantFromContextNeverFromTheCommand() {
        // El comando no tiene campo tenantId; el unico origen posible es el
        // principal autenticado.
        CreateWorkCalendarUseCase otherTenantUseCase = new CreateWorkCalendarUseCase(
                calendarRepository,
                translator,
                tenantContext(OTHER_TENANT_ID),
                domainEventPublisher,
                auditRecorder,
                clock,
                idGenerator);

        assertThat(otherTenantUseCase.create(command("General")).tenantId()).isEqualTo(OTHER_TENANT_ID);
    }

    @Test
    void createRejectsDuplicateNameWithinTenant() {
        when(calendarRepository.existsByName(TENANT_ID, "General")).thenReturn(true);

        assertThatExceptionOfType(DuplicateCalendarNameException.class)
                .isThrownBy(() -> createUseCase.create(command("General")))
                .matches(ex -> ex.errorCode().equals("CALENDAR_NAME_ALREADY_EXISTS"));

        verify(calendarRepository, never()).save(any());
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    void createFallsBackToConfiguredTimezoneWhenNoneIsSupplied() {
        SaveWorkCalendarCommand command =
                new SaveWorkCalendarCommand("Sin zona", null, FROM, null, List.of(), List.of(), List.of());

        assertThat(createUseCase.create(command).timezone()).isEqualTo("Europe/Madrid");
    }

    // --- Editar -----------------------------------------------------------

    @Test
    void updateReplacesStateAndAudits() {
        WorkCalendar calendar = existingCalendar();
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));

        WorkCalendar updated = updateUseCase.update(calendar.id(), command("General"));

        assertThat(updated.holidays()).hasSize(1);
        assertThat(updated.specialDays()).hasSize(1);
        verify(domainEventPublisher).publish(any());
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditRecorder)
                .record(eq("CALENDAR_UPDATED"), eq("WorkCalendar"), eq(calendar.id()), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("holidays", 1).containsEntry("specialDays", 1);
    }

    @Test
    void updateReturns404WhenCalendarBelongsToAnotherTenant() {
        UUID foreignId = UUID.randomUUID();
        // El repositorio esta filtrado por tenant: un id ajeno devuelve vacio.
        when(calendarRepository.findById(TENANT_ID, foreignId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> updateUseCase.update(foreignId, command("General")));
        verify(calendarRepository, never()).save(any());
    }

    @Test
    void updateAcceptsKeepingTheSameNameButRejectsTakingAnother() {
        WorkCalendar calendar = existingCalendar();
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));
        when(calendarRepository.existsByName(eq(TENANT_ID), anyString())).thenReturn(true);

        // Mismo nombre: no se comprueba el duplicado contra si mismo.
        assertThat(updateUseCase.update(calendar.id(), command("General")).name()).isEqualTo("General");

        assertThatExceptionOfType(DuplicateCalendarNameException.class)
                .isThrownBy(() -> updateUseCase.update(calendar.id(), command("Otro nombre")));
    }

    @Test
    void updateRejectsArchivedCalendar() {
        WorkCalendar calendar = existingCalendar();
        calendar.archive(clock, idGenerator);
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));

        assertThatExceptionOfType(CalendarArchivedException.class)
                .isThrownBy(() -> updateUseCase.update(calendar.id(), command("General")));
    }

    // --- Archivar ---------------------------------------------------------

    @Test
    void archiveMarksCalendarPublishesAndAudits() {
        WorkCalendar calendar = existingCalendar();
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));

        WorkCalendar archived = archiveUseCase.archive(calendar.id());

        assertThat(archived.status()).isEqualTo(CalendarStatus.ARCHIVED);
        verify(domainEventPublisher).publish(any());
        verify(auditRecorder).record(eq("CALENDAR_ARCHIVED"), eq("WorkCalendar"), eq(calendar.id()), any());
    }

    @Test
    void archiveReturns404ForUnknownOrForeignCalendar() {
        UUID unknown = UUID.randomUUID();
        when(calendarRepository.findById(TENANT_ID, unknown)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> archiveUseCase.archive(unknown));
    }

    // --- Consultar y listar ------------------------------------------------

    @Test
    void getReturnsCalendarOfCurrentTenantAnd404Otherwise() {
        WorkCalendar calendar = existingCalendar();
        when(calendarRepository.findById(TENANT_ID, calendar.id())).thenReturn(Optional.of(calendar));

        assertThat(getUseCase.get(calendar.id())).isSameAs(calendar);

        UUID foreign = UUID.randomUUID();
        when(calendarRepository.findById(TENANT_ID, foreign)).thenReturn(Optional.empty());
        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> getUseCase.get(foreign));
    }

    @Test
    void listDelegatesTenantScopedPagedQuery() {
        PagedResult<WorkCalendar> page = new PagedResult<>(List.of(existingCalendar()), 0, 20, 1, 1);
        when(calendarRepository.findByTenant(TENANT_ID, CalendarStatus.ACTIVE, 0, 20)).thenReturn(page);

        assertThat(listUseCase.list(CalendarStatus.ACTIVE, 0, 20)).isSameAs(page);
        verify(calendarRepository).findByTenant(TENANT_ID, CalendarStatus.ACTIVE, 0, 20);
    }

    // --- Cotas defensivas del traductor -------------------------------------

    @Test
    void rejectsCommandsExceedingConfiguredLimits() {
        CalendarCommandTranslator strict =
                new CalendarCommandTranslator(new CalendarProperties(1, 1, "Europe/Madrid"));
        SaveWorkCalendarCommand tooManyHolidays = new SaveWorkCalendarCommand(
                "X",
                "UTC",
                FROM,
                null,
                List.of(),
                List.of(
                        new HolidayCommand(LocalDate.of(2026, 1, 6), "Reyes"),
                        new HolidayCommand(LocalDate.of(2026, 5, 1), "Trabajo")),
                List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> strict.holidays(tooManyHolidays))
                .withMessageContaining("festivos");

        SaveWorkCalendarCommand tooManySpecialDays = new SaveWorkCalendarCommand(
                "X",
                "UTC",
                FROM,
                null,
                List.of(),
                List.of(),
                List.of(
                        new SpecialDayCommand(LocalDate.of(2026, 1, 7), "A", 60),
                        new SpecialDayCommand(LocalDate.of(2026, 1, 8), "B", 60)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> strict.specialDays(tooManySpecialDays))
                .withMessageContaining("jornadas especiales");
    }

    @Test
    void commandNormalisesNullCollectionsToEmpty() {
        SaveWorkCalendarCommand command = new SaveWorkCalendarCommand("X", "UTC", FROM, null, null, null, null);

        assertThat(command.dayRules()).isEmpty();
        assertThat(command.holidays()).isEmpty();
        assertThat(command.specialDays()).isEmpty();
        assertThat(translator.dayRules(command)).isEmpty();
        assertThat(translator.holidays(command)).isEmpty();
        assertThat(translator.specialDays(command)).isEmpty();
    }

    @Test
    void propertiesFallBackToSafeDefaultsWhenMisconfigured() {
        CalendarProperties defaults = new CalendarProperties(0, -5, "  ");

        assertThat(defaults.maxHolidaysPerCalendar()).isEqualTo(400);
        assertThat(defaults.maxSpecialDaysPerCalendar()).isEqualTo(400);
        assertThat(defaults.defaultTimezone()).isEqualTo("UTC");
    }
}
