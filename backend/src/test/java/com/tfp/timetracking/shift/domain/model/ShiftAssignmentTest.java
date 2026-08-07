package com.tfp.timetracking.shift.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShiftAssignmentTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TEMPLATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void createsAssignmentWithValidPeriod() {
        ShiftAssignment assignment = ShiftAssignment.create(
                TENANT_ID,
                EMPLOYEE_ID,
                TEMPLATE_ID,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                ASSIGNMENT_ID);

        assertThat(assignment.id()).isEqualTo(ASSIGNMENT_ID);
        assertThat(assignment.isEffectiveOn(LocalDate.of(2026, 9, 15))).isTrue();
        assertThat(assignment.isEffectiveOn(LocalDate.of(2026, 10, 1))).isFalse();
    }

    @Test
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> ShiftAssignment.create(
                        TENANT_ID,
                        EMPLOYEE_ID,
                        TEMPLATE_ID,
                        LocalDate.of(2026, 9, 30),
                        LocalDate.of(2026, 9, 1),
                        ASSIGNMENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsOverlapForSameEmployeeAndTenant() {
        ShiftAssignment first = ShiftAssignment.create(
                TENANT_ID,
                EMPLOYEE_ID,
                TEMPLATE_ID,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 15),
                ASSIGNMENT_ID);
        ShiftAssignment second = ShiftAssignment.create(
                TENANT_ID,
                EMPLOYEE_ID,
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 20),
                UUID.randomUUID());

        assertThat(first.overlaps(second)).isTrue();
    }

    @Test
    void ignoresOverlapAcrossDifferentEmployeesOrTenants() {
        ShiftAssignment base = ShiftAssignment.create(
                TENANT_ID,
                EMPLOYEE_ID,
                TEMPLATE_ID,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 15),
                ASSIGNMENT_ID);
        ShiftAssignment otherEmployee = ShiftAssignment.create(
                TENANT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 20),
                UUID.randomUUID());
        ShiftAssignment otherTenant = ShiftAssignment.create(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 20),
                UUID.randomUUID());

        assertThat(base.overlaps(otherEmployee)).isFalse();
        assertThat(base.overlaps(otherTenant)).isFalse();
    }

    @Test
    void reassignReplacesTemplateAndPeriod() {
        ShiftAssignment assignment = ShiftAssignment.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                UUID.randomUUID());
        UUID newTemplate = UUID.randomUUID();

        assignment.reassign(newTemplate, LocalDate.of(2026, 7, 1), null);

        assertThat(assignment.shiftTemplateId()).isEqualTo(newTemplate);
        assertThat(assignment.validFrom()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(assignment.validTo()).isNull();
    }

    @Test
    void reassignRejectsAnInvertedPeriod() {
        ShiftAssignment assignment = ShiftAssignment.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                null,
                UUID.randomUUID());

        assertThatThrownBy(() ->
                        assignment.reassign(UUID.randomUUID(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOpenEndedAssignmentOverlapsAnythingAfterItsStart() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        ShiftAssignment openEnded = ShiftAssignment.create(
                tenantId, employeeId, UUID.randomUUID(), LocalDate.of(2026, 1, 1), null, UUID.randomUUID());
        ShiftAssignment later = ShiftAssignment.create(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 12, 31),
                UUID.randomUUID());

        assertThat(openEnded.overlaps(later)).isTrue();
        assertThat(later.overlaps(openEnded)).isTrue();
    }

    @Test
    void isEffectiveOnRespectsBothEndsOfThePeriod() {
        ShiftAssignment assignment = ShiftAssignment.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                UUID.randomUUID());

        assertThat(assignment.isEffectiveOn(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(assignment.isEffectiveOn(LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(assignment.isEffectiveOn(LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(assignment.isEffectiveOn(LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    void anOpenEndedAssignmentIsEffectiveIndefinitely() {
        ShiftAssignment assignment = ShiftAssignment.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 3, 1),
                null,
                UUID.randomUUID());

        assertThat(assignment.isEffectiveOn(LocalDate.of(2099, 1, 1))).isTrue();
    }
}
