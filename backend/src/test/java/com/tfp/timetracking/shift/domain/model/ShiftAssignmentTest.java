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
}
