package com.tfp.timetracking.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ObservabilityContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void publishesDeclaredKeys() {
        ObservabilityContext.put(ObservabilityContext.CORRELATION_ID, "cid-1");
        ObservabilityContext.put(ObservabilityContext.TENANT_ID, "tenant-1");
        ObservabilityContext.put(ObservabilityContext.USER_ID, "user-1");
        ObservabilityContext.put(ObservabilityContext.USE_CASE, "Controller#method");
        ObservabilityContext.put(ObservabilityContext.RESULT, ObservabilityContext.RESULT_SUCCESS);

        assertThat(MDC.get("correlationId")).isEqualTo("cid-1");
        assertThat(MDC.get("tenantId")).isEqualTo("tenant-1");
        assertThat(MDC.get("userId")).isEqualTo("user-1");
        assertThat(MDC.get("useCase")).isEqualTo("Controller#method");
        assertThat(MDC.get("result")).isEqualTo("SUCCESS");
        assertThat(ObservabilityContext.currentCorrelationId()).isEqualTo("cid-1");
    }

    /**
     * RS-014: el esquema del log es cerrado. El formateador estructurado vuelca
     * el MDC entero, asi que permitir claves libres seria permitir publicar
     * cualquier cosa —una cookie, un token— en cada linea.
     */
    @Test
    void rejectsUndeclaredKeys() {
        assertThatThrownBy(() -> ObservabilityContext.put("password", "hunter2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
        assertThat(MDC.get("password")).isNull();
    }

    @Test
    void ignoresNullAndBlankValuesInsteadOfLoggingTheStringNull() {
        ObservabilityContext.put(ObservabilityContext.TENANT_ID, null);
        ObservabilityContext.put(ObservabilityContext.USER_ID, "   ");

        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void clearRemovesEveryDeclaredKey() {
        ObservabilityContext.put(ObservabilityContext.CORRELATION_ID, "cid-1");
        ObservabilityContext.put(ObservabilityContext.TENANT_ID, "tenant-1");
        ObservabilityContext.put(ObservabilityContext.USER_ID, "user-1");
        ObservabilityContext.put(ObservabilityContext.USE_CASE, "uc");
        ObservabilityContext.put(ObservabilityContext.RESULT, "SUCCESS");

        ObservabilityContext.clear();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        assertThat(ObservabilityContext.currentCorrelationId()).isNull();
    }

    @Test
    void generatesDistinctCorrelationIds() {
        assertThat(ObservabilityContext.newCorrelationId()).isNotEqualTo(ObservabilityContext.newCorrelationId());
    }
}
