package com.tfp.timetracking.absence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.absence.domain.event.AbsenceApproved;
import com.tfp.timetracking.absence.domain.event.AbsenceCancelled;
import com.tfp.timetracking.absence.domain.event.AbsenceRejected;
import com.tfp.timetracking.absence.domain.event.AbsenceRequested;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbsenceRequestTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ABSENCE_TYPE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");

    @Test
    void requestCreatesPendingAbsenceAndEvent() {
        UUID requestId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        AbsenceRequest request = AbsenceRequest.request(
                TENANT_ID,
                EMPLOYEE_ID,
                ABSENCE_TYPE_ID,
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-12"),
                "Vacaciones de verano",
                NOW,
                fixedIdGenerator(requestId));

        assertThat(request.id()).isEqualTo(requestId);
        assertThat(request.status()).isEqualTo(AbsenceRequestStatus.PENDING);
        assertThat(request.reason()).isEqualTo("Vacaciones de verano");
        assertThat(request.pullDomainEvents()).singleElement().isInstanceOfSatisfying(AbsenceRequested.class, event -> {
            assertThat(event.aggregateId()).isEqualTo(requestId);
            assertThat(event.startDate()).isEqualTo(LocalDate.parse("2026-08-10"));
            assertThat(event.endDate()).isEqualTo(LocalDate.parse("2026-08-12"));
        });
    }

    @Test
    void approveRejectAndCancelChangeStateAndEmitEvents() {
        AbsenceRequest approved = newRequest();
        approved.approve(UUID.randomUUID(), "Aprobada", NOW.plusSeconds(60), UUID::randomUUID);
        assertThat(approved.status()).isEqualTo(AbsenceRequestStatus.APPROVED);
        assertThat(approved.pullDomainEvents().getFirst()).isInstanceOf(AbsenceApproved.class);

        AbsenceRequest rejected = newRequest();
        rejected.reject(UUID.randomUUID(), "No procede", NOW.plusSeconds(60), UUID::randomUUID);
        assertThat(rejected.status()).isEqualTo(AbsenceRequestStatus.REJECTED);
        assertThat(rejected.pullDomainEvents().getFirst()).isInstanceOf(AbsenceRejected.class);

        AbsenceRequest cancelled = newRequest();
        cancelled.cancel(NOW.plusSeconds(60), UUID::randomUUID);
        assertThat(cancelled.status()).isEqualTo(AbsenceRequestStatus.CANCELLED);
        assertThat(cancelled.pullDomainEvents().getFirst()).isInstanceOf(AbsenceCancelled.class);
    }

    @Test
    void rejectsInvalidRangesAndResolvingTwice() {
        assertThatThrownBy(() -> AbsenceRequest.request(
                        TENANT_ID,
                        EMPLOYEE_ID,
                        ABSENCE_TYPE_ID,
                        LocalDate.parse("2026-08-12"),
                        LocalDate.parse("2026-08-10"),
                        "Inválida",
                        NOW,
                        UUID::randomUUID))
                .isInstanceOf(IllegalArgumentException.class);

        AbsenceRequest request = newRequest();
        request.approve(UUID.randomUUID(), "Ok", NOW.plusSeconds(60), UUID::randomUUID);
        request.pullDomainEvents();

        assertThatThrownBy(() -> request.cancel(NOW.plusSeconds(120), UUID::randomUUID))
                .isInstanceOf(AbsenceRequestAlreadyResolvedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ABSENCE_REQUEST_ALREADY_RESOLVED");
    }

    @Test
    void reconstituteDoesNotGenerateEvents() {
        AbsenceRequest request = AbsenceRequest.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                EMPLOYEE_ID,
                ABSENCE_TYPE_ID,
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-12"),
                "Vacaciones",
                AbsenceRequestStatus.PENDING,
                null,
                null,
                null,
                NOW);

        assertThat(request.pullDomainEvents()).isEmpty();
    }

    private AbsenceRequest newRequest() {
        AbsenceRequest request = AbsenceRequest.request(
                TENANT_ID,
                EMPLOYEE_ID,
                ABSENCE_TYPE_ID,
                LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-08-12"),
                "Vacaciones",
                NOW,
                UUID::randomUUID);
        request.pullDomainEvents();
        return request;
    }

    private static IdGenerator fixedIdGenerator(UUID firstId) {
        Deque<UUID> ids = new ArrayDeque<>();
        ids.add(firstId);
        return () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();
    }
}
