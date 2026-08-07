package com.tfp.timetracking.absence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbsenceTypeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TYPE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void createsAValidAbsenceType() {
        AbsenceType type = AbsenceType.create(TENANT_ID, "vacaciones", "Vacaciones", true, false, TYPE_ID);

        assertThat(type.id()).isEqualTo(TYPE_ID);
        assertThat(type.tenantId()).isEqualTo(TENANT_ID);
        assertThat(type.code()).isEqualTo("VACACIONES");
        assertThat(type.name()).isEqualTo("Vacaciones");
        assertThat(type.requiresApproval()).isTrue();
        assertThat(type.allowsAttachment()).isFalse();
        assertThat(type.active()).isTrue();
    }

    @Test
    void rejectsBlankCodeOrName() {
        assertThatThrownBy(() -> AbsenceType.create(TENANT_ID, " ", "Vacaciones", true, false, TYPE_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AbsenceType.create(TENANT_ID, "VAC", " ", true, false, TYPE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canRenameReconfigureAndDeactivate() {
        AbsenceType type = AbsenceType.create(TENANT_ID, "VAC", "Vacaciones", true, false, TYPE_ID);

        type.rename("Permiso retribuido");
        type.configure(false, true);
        type.deactivate();

        assertThat(type.name()).isEqualTo("Permiso retribuido");
        assertThat(type.requiresApproval()).isFalse();
        assertThat(type.allowsAttachment()).isTrue();
        assertThat(type.active()).isFalse();

        type.activate();
        assertThat(type.active()).isTrue();
    }

    @Test
    void reconstitutePreservesState() {
        AbsenceType type = AbsenceType.reconstitute(TYPE_ID, TENANT_ID, "med", "Baja médica", false, true, false);

        assertThat(type.code()).isEqualTo("MED");
        assertThat(type.name()).isEqualTo("Baja médica");
        assertThat(type.requiresApproval()).isFalse();
        assertThat(type.allowsAttachment()).isTrue();
        assertThat(type.active()).isFalse();
    }
}
