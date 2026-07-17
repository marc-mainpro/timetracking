package com.tfp.timetracking.corrections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.corrections.domain.event.CorrectionApproved;
import com.tfp.timetracking.corrections.domain.event.CorrectionRejected;
import com.tfp.timetracking.corrections.domain.event.CorrectionRequested;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrectionRequestTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKDAY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REQUESTED_BY = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void requestCreatesPendingCorrectionAndEvent() {
        UUID correctionId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        CorrectionRequest request = CorrectionRequest.request(
                TENANT_ID,
                WORKDAY_ID,
                REQUESTED_BY,
                "Olvido de salida",
                validChanges(),
                NOW,
                fixedIdGenerator(correctionId));

        assertThat(request.id()).isEqualTo(correctionId);
        assertThat(request.status()).isEqualTo(CorrectionRequestStatus.PENDING);
        assertThat(request.reason()).isEqualTo("Olvido de salida");
        List<Object> events = request.pullDomainEvents();
        assertThat(events).hasSize(1);
        CorrectionRequested event = (CorrectionRequested) events.get(0);
        assertThat(event.aggregateId()).isEqualTo(correctionId);
        assertThat(event.workdayId()).isEqualTo(WORKDAY_ID);
    }

    @Test
    void approveMarksRequestApprovedAndGeneratesEvent() {
        CorrectionRequest request = newRequest();
        UUID resolvedBy = UUID.randomUUID();

        request.approve(resolvedBy, "Ok", NOW.plusSeconds(60), UUID::randomUUID);

        assertThat(request.status()).isEqualTo(CorrectionRequestStatus.APPROVED);
        assertThat(request.resolvedBy()).isEqualTo(resolvedBy);
        assertThat(request.resolutionComment()).isEqualTo("Ok");
        assertThat(request.pullDomainEvents().get(0)).isInstanceOf(CorrectionApproved.class);
    }

    @Test
    void rejectMarksRequestRejectedAndGeneratesEvent() {
        CorrectionRequest request = newRequest();
        UUID resolvedBy = UUID.randomUUID();

        request.reject(resolvedBy, "No procede", NOW.plusSeconds(60), UUID::randomUUID);

        assertThat(request.status()).isEqualTo(CorrectionRequestStatus.REJECTED);
        assertThat(request.pullDomainEvents().get(0)).isInstanceOf(CorrectionRejected.class);
    }

    @Test
    void rejectsResolvingTwice() {
        CorrectionRequest request = newRequest();
        request.approve(UUID.randomUUID(), "Ok", NOW.plusSeconds(60), UUID::randomUUID);
        request.pullDomainEvents();

        assertThatThrownBy(() -> request.reject(UUID.randomUUID(), "No", NOW.plusSeconds(120), UUID::randomUUID))
                .isInstanceOf(CorrectionAlreadyResolvedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CORRECTION_ALREADY_RESOLVED");
    }

    @Test
    void reconstituteDoesNotGenerateDomainEvents() {
        CorrectionRequest request = CorrectionRequest.reconstitute(
                UUID.randomUUID(),
                TENANT_ID,
                WORKDAY_ID,
                REQUESTED_BY,
                "Cambio horario",
                validChanges(),
                CorrectionRequestStatus.PENDING,
                null,
                null,
                null,
                NOW);

        assertThat(request.pullDomainEvents()).isEmpty();
    }

    private CorrectionRequest newRequest() {
        CorrectionRequest request = CorrectionRequest.request(
                TENANT_ID,
                WORKDAY_ID,
                REQUESTED_BY,
                "Olvido de salida",
                validChanges(),
                NOW,
                UUID::randomUUID);
        request.pullDomainEvents();
        return request;
    }

    private ProposedChanges validChanges() {
        return new ProposedChanges(
                Instant.parse("2026-01-15T09:00:00Z"),
                Instant.parse("2026-01-15T18:00:00Z"),
                List.of(new ProposedChanges.ProposedBreak(
                        Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))));
    }

    private static IdGenerator fixedIdGenerator(UUID firstId) {
        Deque<UUID> ids = new ArrayDeque<>();
        ids.add(firstId);
        return () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();
    }
}
