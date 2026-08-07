package com.tfp.timetracking.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformTenantTest {

    @Test
    void exposesFixedSystemTenantId() {
        assertThat(PlatformTenant.ID).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }
}
