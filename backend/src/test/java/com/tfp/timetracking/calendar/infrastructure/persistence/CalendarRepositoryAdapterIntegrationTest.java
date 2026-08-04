package com.tfp.timetracking.calendar.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.calendar.domain.model.AssignmentScope;
import com.tfp.timetracking.calendar.domain.model.CalendarAssignment;
import com.tfp.timetracking.calendar.domain.model.CalendarDayRule;
import com.tfp.timetracking.calendar.domain.model.CalendarStatus;
import com.tfp.timetracking.calendar.domain.model.Holiday;
import com.tfp.timetracking.calendar.domain.model.SpecialDay;
import com.tfp.timetracking.calendar.domain.model.WorkCalendar;
import com.tfp.timetracking.calendar.domain.repository.CalendarAssignmentRepository;
import com.tfp.timetracking.calendar.domain.repository.WorkCalendarRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Adaptadores de persistencia del modulo {@code calendar} contra PostgreSQL
 * real. Cubre el viaje de ida y vuelta del agregado con sus colecciones hijas y
 * —lo importante— el <b>aislamiento por tenant</b> (RT-003): ninguna consulta
 * puede devolver datos de otra organizacion.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalendarRepositoryAdapterIntegrationTest {

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
    private WorkCalendarRepository calendarRepository;

    @Autowired
    private CalendarAssignmentRepository assignmentRepository;

    @Autowired
    private DataSource dataSource;

    private final Clock clock = () -> Instant.parse("2026-01-15T09:00:00Z");
    private final IdGenerator idGenerator = UUID::randomUUID;

    private UUID newTenant(String label) {
        UUID tenantId = UUID.randomUUID();
        new JdbcTemplate(dataSource)
                .update(
                        """
                        INSERT INTO tenant (id, name, status, timezone, created_at, updated_at, activated_at)
                        VALUES (?, ?, 'ACTIVE', 'Europe/Madrid', now(), now(), now())
                        """,
                        tenantId,
                        label + " " + tenantId);
        return tenantId;
    }

    private WorkCalendar newCalendar(UUID tenantId, String name) {
        return calendarRepository.save(WorkCalendar.create(
                tenantId,
                name,
                "Europe/Madrid",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(
                        CalendarDayRule.working(DayOfWeek.MONDAY, 480),
                        CalendarDayRule.nonWorking(DayOfWeek.SUNDAY)),
                List.of(new Holiday(LocalDate.of(2026, 1, 6), "Reyes")),
                List.of(new SpecialDay(LocalDate.of(2026, 12, 24), "Intensiva", 300)),
                clock,
                idGenerator));
    }

    @Test
    void persistsAndReloadsTheWholeAggregate() {
        UUID tenantId = newTenant("Persist");
        WorkCalendar saved = newCalendar(tenantId, "General " + UUID.randomUUID());

        WorkCalendar reloaded = calendarRepository.findById(tenantId, saved.id()).orElseThrow();

        assertThat(reloaded.name()).isEqualTo(saved.name());
        assertThat(reloaded.timezone()).isEqualTo("Europe/Madrid");
        assertThat(reloaded.validFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(reloaded.validTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(reloaded.status()).isEqualTo(CalendarStatus.ACTIVE);
        assertThat(reloaded.dayRules()).hasSize(2);
        assertThat(reloaded.holidays()).containsExactly(new Holiday(LocalDate.of(2026, 1, 6), "Reyes"));
        assertThat(reloaded.specialDays())
                .containsExactly(new SpecialDay(LocalDate.of(2026, 12, 24), "Intensiva", 300));
        // Las reglas de negocio sobreviven al viaje de ida y vuelta.
        assertThat(reloaded.isWorkingDay(LocalDate.of(2026, 3, 2))).isTrue();
        assertThat(reloaded.isWorkingDay(LocalDate.of(2026, 1, 6))).isFalse();
    }

    @Test
    void updateReplacesChildCollectionsInsteadOfAccumulatingThem() {
        UUID tenantId = newTenant("Replace");
        WorkCalendar saved = newCalendar(tenantId, "Editable " + UUID.randomUUID());

        WorkCalendar loaded = calendarRepository.findById(tenantId, saved.id()).orElseThrow();
        loaded.update(
                loaded.name(),
                "Atlantic/Canary",
                LocalDate.of(2026, 2, 1),
                null,
                List.of(CalendarDayRule.working(DayOfWeek.TUESDAY, 300)),
                List.of(),
                List.of(),
                clock,
                idGenerator);
        calendarRepository.save(loaded);

        WorkCalendar reloaded = calendarRepository.findById(tenantId, saved.id()).orElseThrow();
        assertThat(reloaded.timezone()).isEqualTo("Atlantic/Canary");
        assertThat(reloaded.validTo()).isNull();
        assertThat(reloaded.dayRules()).hasSize(1);
        assertThat(reloaded.holidays()).isEmpty();
        assertThat(reloaded.specialDays()).isEmpty();
    }

    @Test
    void doesNotReturnCalendarsOfAnotherTenant() {
        UUID tenantA = newTenant("Cross A");
        UUID tenantB = newTenant("Cross B");
        WorkCalendar ofA = newCalendar(tenantA, "Privado " + UUID.randomUUID());

        // Mismo id, tenant equivocado: vacio, para que el caso de uso responda 404.
        assertThat(calendarRepository.findById(tenantB, ofA.id())).isEmpty();
        assertThat(calendarRepository.findAllByIds(tenantB, List.of(ofA.id()))).isEmpty();
        assertThat(calendarRepository.findByTenant(tenantB, null, 0, 20).content()).isEmpty();
        assertThat(calendarRepository.existsByName(tenantB, ofA.name())).isFalse();
        assertThat(calendarRepository.existsByName(tenantA, ofA.name())).isTrue();
    }

    @Test
    void findAllByIdsIgnoresEmptyInputAndForeignIds() {
        UUID tenantId = newTenant("Ids");
        WorkCalendar first = newCalendar(tenantId, "Uno " + UUID.randomUUID());

        assertThat(calendarRepository.findAllByIds(tenantId, List.of())).isEmpty();
        assertThat(calendarRepository.findAllByIds(tenantId, null)).isEmpty();
        assertThat(calendarRepository.findAllByIds(tenantId, List.of(first.id(), UUID.randomUUID())))
                .extracting(WorkCalendar::id)
                .containsExactly(first.id());
    }

    @Test
    void filtersListingByStatus() {
        UUID tenantId = newTenant("Status");
        WorkCalendar active = newCalendar(tenantId, "Activo " + UUID.randomUUID());
        WorkCalendar toArchive = newCalendar(tenantId, "Archivado " + UUID.randomUUID());
        toArchive.archive(clock, idGenerator);
        calendarRepository.save(toArchive);

        assertThat(calendarRepository.findByTenant(tenantId, CalendarStatus.ACTIVE, 0, 20).content())
                .extracting(WorkCalendar::id)
                .containsExactly(active.id());
        assertThat(calendarRepository.findByTenant(tenantId, CalendarStatus.ARCHIVED, 0, 20).content())
                .extracting(WorkCalendar::id)
                .containsExactly(toArchive.id());
        assertThat(calendarRepository.findByTenant(tenantId, null, 0, 20).totalElements())
                .isEqualTo(2);
    }

    // --- Asignaciones -------------------------------------------------------

    @Test
    void persistsAssignmentsAndFindsThemByScope() {
        UUID tenantId = newTenant("Assign");
        WorkCalendar calendar = newCalendar(tenantId, "Asignable " + UUID.randomUUID());
        UUID employeeId = UUID.randomUUID();

        CalendarAssignment tenantWide = assignmentRepository.save(CalendarAssignment.create(
                tenantId, calendar.id(), AssignmentScope.TENANT, null, clock, idGenerator));
        CalendarAssignment forEmployee = assignmentRepository.save(CalendarAssignment.create(
                tenantId, calendar.id(), AssignmentScope.EMPLOYEE, employeeId, clock, idGenerator));

        // findByScope con targetId null usa el "IS NULL" explicito.
        assertThat(assignmentRepository.findByScope(tenantId, AssignmentScope.TENANT, null))
                .map(CalendarAssignment::id)
                .contains(tenantWide.id());
        assertThat(assignmentRepository.findByScope(tenantId, AssignmentScope.EMPLOYEE, employeeId))
                .map(CalendarAssignment::id)
                .contains(forEmployee.id());
        assertThat(assignmentRepository.findByScope(tenantId, AssignmentScope.EMPLOYEE, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void findApplicableNarrowsToTenantTeamAndEmployeeOfTheCaller() {
        UUID tenantId = newTenant("Applicable");
        WorkCalendar calendar = newCalendar(tenantId, "Aplicable " + UUID.randomUUID());
        UUID employeeId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        assignmentRepository.save(CalendarAssignment.create(
                tenantId, calendar.id(), AssignmentScope.TENANT, null, clock, idGenerator));
        assignmentRepository.save(CalendarAssignment.create(
                tenantId, calendar.id(), AssignmentScope.TEAM, teamId, clock, idGenerator));
        assignmentRepository.save(CalendarAssignment.create(
                tenantId, calendar.id(), AssignmentScope.EMPLOYEE, employeeId, clock, idGenerator));
        // Ruido: otro empleado y otro equipo.
        assignmentRepository.save(CalendarAssignment.create(
                tenantId, calendar.id(), AssignmentScope.EMPLOYEE, UUID.randomUUID(), clock, idGenerator));
        assignmentRepository.save(CalendarAssignment.create(
                tenantId, calendar.id(), AssignmentScope.TEAM, UUID.randomUUID(), clock, idGenerator));

        assertThat(assignmentRepository.findApplicable(tenantId, employeeId, teamId))
                .extracting(CalendarAssignment::scope)
                .containsExactlyInAnyOrder(
                        AssignmentScope.TENANT, AssignmentScope.TEAM, AssignmentScope.EMPLOYEE);

        // Sin equipo, la asignacion de equipo no se trae siquiera de la base.
        assertThat(assignmentRepository.findApplicable(tenantId, employeeId, null))
                .extracting(CalendarAssignment::scope)
                .containsExactlyInAnyOrder(AssignmentScope.TENANT, AssignmentScope.EMPLOYEE);
    }

    @Test
    void doesNotReturnOrDeleteAssignmentsOfAnotherTenant() {
        UUID tenantA = newTenant("Assign A");
        UUID tenantB = newTenant("Assign B");
        WorkCalendar calendarA = newCalendar(tenantA, "De A " + UUID.randomUUID());
        CalendarAssignment ofA = assignmentRepository.save(CalendarAssignment.create(
                tenantA, calendarA.id(), AssignmentScope.TENANT, null, clock, idGenerator));

        assertThat(assignmentRepository.findById(tenantB, ofA.id())).isEmpty();
        assertThat(assignmentRepository.findByTenant(tenantB, null, 0, 20).content()).isEmpty();
        assertThat(assignmentRepository.findApplicable(tenantB, UUID.randomUUID(), null))
                .isEmpty();

        // Un borrado con el tenant equivocado no toca la fila ajena.
        assignmentRepository.delete(tenantB, ofA.id());
        assertThat(assignmentRepository.findById(tenantA, ofA.id())).isPresent();

        assignmentRepository.delete(tenantA, ofA.id());
        assertThat(assignmentRepository.findById(tenantA, ofA.id())).isEmpty();
        // Borrar algo inexistente no falla.
        assignmentRepository.delete(tenantA, ofA.id());
    }

    @Test
    void filtersAssignmentListingByCalendar() {
        UUID tenantId = newTenant("Filter");
        WorkCalendar first = newCalendar(tenantId, "Primero " + UUID.randomUUID());
        WorkCalendar second = newCalendar(tenantId, "Segundo " + UUID.randomUUID());
        assignmentRepository.save(CalendarAssignment.create(
                tenantId, first.id(), AssignmentScope.TENANT, null, clock, idGenerator));
        assignmentRepository.save(CalendarAssignment.create(
                tenantId, second.id(), AssignmentScope.EMPLOYEE, UUID.randomUUID(), clock, idGenerator));

        assertThat(assignmentRepository.findByTenant(tenantId, first.id(), 0, 20).content())
                .extracting(CalendarAssignment::calendarId)
                .containsExactly(first.id());
        assertThat(assignmentRepository.findByTenant(tenantId, null, 0, 20).totalElements())
                .isEqualTo(2);
    }
}
