package com.tfp.timetracking.shift.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShiftTemplateTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEMPLATE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void createsADayShiftWithoutCrossingMidnight() {
        ShiftTemplate template = ShiftTemplate.create(
                TENANT_ID,
                "Turno mañana",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                new ShiftBreakPolicy(Duration.ofMinutes(30)),
                TEMPLATE_ID);

        assertThat(template.crossesMidnight()).isFalse();
        assertThat(template.plannedDuration()).isEqualTo(Duration.ofHours(8));
        assertThat(template.status()).isEqualTo(ShiftTemplateStatus.ACTIVE);
    }

    @Test
    void createsANightShiftCrossingMidnight() {
        ShiftTemplate template = ShiftTemplate.create(
                TENANT_ID,
                "Turno noche",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                new ShiftBreakPolicy(Duration.ofMinutes(20)),
                TEMPLATE_ID);

        assertThat(template.crossesMidnight()).isTrue();
        assertThat(template.plannedDuration()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void rejectsBreakPolicyGreaterThanOrEqualToShiftDuration() {
        assertThatThrownBy(() -> ShiftTemplate.create(
                        TENANT_ID,
                        "Inválido",
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 0),
                        new ShiftBreakPolicy(Duration.ofHours(1)),
                        TEMPLATE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canUpdateAndArchive() {
        ShiftTemplate template = ShiftTemplate.create(
                TENANT_ID,
                "Turno base",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                new ShiftBreakPolicy(Duration.ofMinutes(30)),
                TEMPLATE_ID);

        template.update(
                "Turno tarde",
                LocalTime.of(14, 0),
                LocalTime.of(22, 0),
                new ShiftBreakPolicy(Duration.ofMinutes(15)));

        assertThat(template.name()).isEqualTo("Turno tarde");
        assertThat(template.breakPolicy().plannedBreakDuration()).isEqualTo(Duration.ofMinutes(15));

        template.archive();
        assertThat(template.status()).isEqualTo(ShiftTemplateStatus.ARCHIVED);

        assertThatThrownBy(() -> template.update(
                        "Otro",
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0),
                        new ShiftBreakPolicy(Duration.ZERO)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> ShiftTemplate.create(
                        UUID.randomUUID(),
                        "  ",
                        LocalTime.of(9, 0),
                        LocalTime.of(17, 0),
                        new ShiftBreakPolicy(Duration.ZERO),
                        UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void rejectsANameLongerThanTheLimit() {
        assertThatThrownBy(() -> ShiftTemplate.create(
                        UUID.randomUUID(),
                        "T".repeat(121),
                        LocalTime.of(9, 0),
                        LocalTime.of(17, 0),
                        new ShiftBreakPolicy(Duration.ZERO),
                        UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120");
    }

    @Test
    void treatsAMissingBreakPolicyAsNoBreak() {
        ShiftTemplate template = ShiftTemplate.create(
                UUID.randomUUID(), "Turno", LocalTime.of(9, 0), LocalTime.of(17, 0), null, UUID.randomUUID());

        assertThat(template.breakPolicy().plannedBreakDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void rejectsAShiftThatStartsAndEndsAtTheSameTime() {
        // Mismo inicio y fin no es un turno de 24 h: es un turno vacio.
        assertThatThrownBy(() -> ShiftTemplate.create(
                        UUID.randomUUID(),
                        "Turno",
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 0),
                        new ShiftBreakPolicy(Duration.ofDays(1)),
                        UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archivedTemplateExceptionCarriesAStableErrorCode() {
        // El codigo viaja al cliente en el Problem Details y el frontend lo
        // traduce: cambiarlo rompe el mensaje de la interfaz.
        ShiftTemplateArchivedException exception = new ShiftTemplateArchivedException();

        assertThat(exception.errorCode()).isEqualTo("SHIFT_TEMPLATE_ARCHIVED");
        assertThat(exception.getMessage()).contains("archivada");
    }
}
