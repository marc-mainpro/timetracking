package com.tfp.timetracking.timetracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.event.BreakEnded;
import com.tfp.timetracking.timetracking.domain.event.BreakStarted;
import com.tfp.timetracking.timetracking.domain.event.WorkdayClosed;
import com.tfp.timetracking.timetracking.domain.event.WorkdayStarted;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkdayTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void startsOpenWorkdayWithGeneratedIdsAndTimestamps() {
        UUID workdayId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(workdayId));

        assertThat(workday.id()).isEqualTo(workdayId);
        assertThat(workday.tenantId()).isEqualTo(TENANT_ID);
        assertThat(workday.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(workday.status()).isEqualTo(WorkdayStatus.OPEN);
        assertThat(workday.startedAt()).isEqualTo(FIXED_NOW);
        assertThat(workday.endedAt()).isNull();
        assertThat(workday.version()).isZero();
        assertThat(workday.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(workday.updatedAt()).isEqualTo(FIXED_NOW);
        assertThat(workday.breaks()).isEmpty();
    }

    @Test
    void startGeneratesWorkdayStartedEventWithMinimalData() {
        UUID workdayId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(workdayId));

        List<Object> events = workday.pullDomainEvents();
        assertThat(events).hasSize(1);
        WorkdayStarted event = (WorkdayStarted) events.get(0);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.aggregateId()).isEqualTo(workdayId);
        assertThat(event.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(event.startedAt()).isEqualTo(FIXED_NOW);
        assertThat(event.occurredAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void pullDomainEventsClearsAccumulatedEvents() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));

        workday.pullDomainEvents();

        assertThat(workday.pullDomainEvents()).isEmpty();
    }

    @Test
    void reconstituteDoesNotGenerateDomainEvents() {
        Workday workday = Workday.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                EMPLOYEE_ID,
                WorkdayStatus.OPEN,
                FIXED_NOW,
                null,
                3L,
                FIXED_NOW,
                FIXED_NOW,
                List.of());

        assertThat(workday.pullDomainEvents()).isEmpty();
    }

    @Test
    void startBreakMovesOpenWorkdayToOnBreakAndGeneratesEvent() {
        UUID workdayId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID breakId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(workdayId));
        workday.pullDomainEvents();
        Instant breakStartedAt = FIXED_NOW.plusSeconds(300);

        workday.startBreak(breakStartedAt, fixedIdGenerator(breakId));

        assertThat(workday.status()).isEqualTo(WorkdayStatus.ON_BREAK);
        assertThat(workday.breaks()).hasSize(1);
        BreakEntry breakEntry = workday.breaks().get(0);
        assertThat(breakEntry.id()).isEqualTo(breakId);
        assertThat(breakEntry.startedAt()).isEqualTo(breakStartedAt);
        assertThat(breakEntry.endedAt()).isNull();
        List<Object> events = workday.pullDomainEvents();
        assertThat(events).hasSize(1);
        BreakStarted event = (BreakStarted) events.get(0);
        assertThat(event.breakId()).isEqualTo(breakId);
        assertThat(event.aggregateId()).isEqualTo(workdayId);
        assertThat(event.startedAt()).isEqualTo(breakStartedAt);
    }

    @Test
    void rejectsStartBreakWhenWorkdayIsOnBreak() {
        Workday workday = startedBreakWorkday();

        assertThatThrownBy(() -> workday.startBreak(FIXED_NOW.plusSeconds(600), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(BreakAlreadyOpenException.class)
                .hasFieldOrPropertyWithValue("errorCode", "BREAK_ALREADY_OPEN");
    }

    @Test
    void rejectsStartBreakWhenWorkdayIsClosed() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        workday.close(FIXED_NOW.plusSeconds(120), fixedIdGenerator(UUID.randomUUID()));

        assertThatThrownBy(() -> workday.startBreak(FIXED_NOW.plusSeconds(180), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(WorkdayNotOpenException.class);
    }

    @Test
    void endBreakMovesOnBreakWorkdayBackToOpenAndGeneratesEvent() {
        UUID workdayId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID breakId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(workdayId));
        workday.pullDomainEvents();
        workday.startBreak(FIXED_NOW.plusSeconds(60), fixedIdGenerator(breakId));
        workday.pullDomainEvents();
        Instant breakEndedAt = FIXED_NOW.plusSeconds(120);

        workday.endBreak(breakEndedAt, fixedIdGenerator(UUID.randomUUID()));

        assertThat(workday.status()).isEqualTo(WorkdayStatus.OPEN);
        assertThat(workday.breaks().get(0).endedAt()).isEqualTo(breakEndedAt);
        List<Object> events = workday.pullDomainEvents();
        assertThat(events).hasSize(1);
        BreakEnded event = (BreakEnded) events.get(0);
        assertThat(event.breakId()).isEqualTo(breakId);
        assertThat(event.aggregateId()).isEqualTo(workdayId);
        assertThat(event.endedAt()).isEqualTo(breakEndedAt);
    }

    @Test
    void rejectsEndBreakWhenNoBreakIsOpen() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));

        assertThatThrownBy(() -> workday.endBreak(FIXED_NOW.plusSeconds(60), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(BreakNotOpenException.class)
                .hasFieldOrPropertyWithValue("errorCode", "BREAK_NOT_OPEN");
    }

    @Test
    void closeMovesOpenWorkdayToClosedAndGeneratesEvent() {
        UUID workdayId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(workdayId));
        workday.pullDomainEvents();
        Instant closedAt = FIXED_NOW.plusSeconds(480);

        workday.close(closedAt, fixedIdGenerator(UUID.randomUUID()));

        assertThat(workday.status()).isEqualTo(WorkdayStatus.CLOSED);
        assertThat(workday.endedAt()).isEqualTo(closedAt);
        List<Object> events = workday.pullDomainEvents();
        assertThat(events).hasSize(1);
        WorkdayClosed event = (WorkdayClosed) events.get(0);
        assertThat(event.aggregateId()).isEqualTo(workdayId);
        assertThat(event.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(event.startedAt()).isEqualTo(FIXED_NOW);
        assertThat(event.endedAt()).isEqualTo(closedAt);
    }

    @Test
    void rejectsCloseWhenBreakIsOpen() {
        Workday workday = startedBreakWorkday();

        assertThatThrownBy(() -> workday.close(FIXED_NOW.plusSeconds(180), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(WorkdayOpenBreakException.class)
                .hasFieldOrPropertyWithValue("errorCode", "WORKDAY_OPEN_BREAK");
    }

    @Test
    void rejectsDoubleClose() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        workday.close(FIXED_NOW.plusSeconds(60), fixedIdGenerator(UUID.randomUUID()));

        assertThatThrownBy(() -> workday.close(FIXED_NOW.plusSeconds(120), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(WorkdayAlreadyClosedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "WORKDAY_ALREADY_CLOSED");
    }

    @Test
    void adjustMovesClosedWorkdayToAdjusted() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        workday.close(FIXED_NOW.plusSeconds(480), fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        WorkdayAdjustment adjustment = new WorkdayAdjustment(
                FIXED_NOW.minusSeconds(60),
                FIXED_NOW.plusSeconds(540),
                List.of(new WorkdayAdjustment.AdjustedBreak(FIXED_NOW.plusSeconds(120), FIXED_NOW.plusSeconds(180))));

        workday.adjust(adjustment, FIXED_NOW.plusSeconds(600), fixedIdGenerator(UUID.randomUUID()));

        assertThat(workday.status()).isEqualTo(WorkdayStatus.ADJUSTED);
        assertThat(workday.startedAt()).isEqualTo(FIXED_NOW.minusSeconds(60));
        assertThat(workday.endedAt()).isEqualTo(FIXED_NOW.plusSeconds(540));
        assertThat(workday.breaks()).hasSize(1);
        assertThat(workday.breaks().get(0).startedAt()).isEqualTo(FIXED_NOW.plusSeconds(120));
        assertThat(workday.breaks().get(0).endedAt()).isEqualTo(FIXED_NOW.plusSeconds(180));
        assertThat(workday.pullDomainEvents()).isEmpty();
    }

    @Test
    void rejectsAdjustWhenWorkdayIsOpen() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));

        assertThatThrownBy(() -> workday.adjust(validAdjustment(), FIXED_NOW.plusSeconds(600), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(WorkdayNotOpenException.class);
    }

    @Test
    void rejectsAdjustWhenWorkdayIsOnBreak() {
        Workday workday = startedBreakWorkday();

        assertThatThrownBy(() -> workday.adjust(validAdjustment(), FIXED_NOW.plusSeconds(600), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(WorkdayOpenBreakException.class);
    }

    @Test
    void rejectsAdjustWhenAlreadyAdjusted() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        workday.close(FIXED_NOW.plusSeconds(120), fixedIdGenerator(UUID.randomUUID()));
        workday.adjust(validAdjustment(), FIXED_NOW.plusSeconds(180), fixedIdGenerator(UUID.randomUUID()));

        assertThatThrownBy(() -> workday.adjust(validAdjustment(), FIXED_NOW.plusSeconds(240), fixedIdGenerator(UUID.randomUUID())))
                .isInstanceOf(WorkdayAlreadyClosedException.class);
    }

    @Test
    void rejectsBreakThatEndsBeforeItStarts() {
        assertThatIllegalArgumentException().isThrownBy(() -> BreakEntry.reconstitute(
                UUID.randomUUID(), FIXED_NOW.plusSeconds(60), FIXED_NOW.plusSeconds(30)));
    }

    @Test
    void rejectsClosingBeforeStartInstant() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> workday.close(FIXED_NOW.minusSeconds(1), fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void breaksAccessorReturnsDefensiveCopy() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        workday.startBreak(FIXED_NOW.plusSeconds(60), fixedIdGenerator(UUID.randomUUID()));

        assertThatThrownBy(() -> workday.breaks().add(BreakEntry.reconstitute(UUID.randomUUID(), FIXED_NOW, FIXED_NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Workday startedBreakWorkday() {
        Workday workday = Workday.start(TENANT_ID, EMPLOYEE_ID, FIXED_NOW, fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        workday.startBreak(FIXED_NOW.plusSeconds(60), fixedIdGenerator(UUID.randomUUID()));
        workday.pullDomainEvents();
        return workday;
    }

    private WorkdayAdjustment validAdjustment() {
        return new WorkdayAdjustment(
                FIXED_NOW,
                FIXED_NOW.plusSeconds(300),
                List.of(new WorkdayAdjustment.AdjustedBreak(FIXED_NOW.plusSeconds(60), FIXED_NOW.plusSeconds(120))));
    }

    private static IdGenerator fixedIdGenerator(UUID firstId) {
        Deque<UUID> ids = new ArrayDeque<>();
        ids.add(firstId);
        return () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();
    }
}
