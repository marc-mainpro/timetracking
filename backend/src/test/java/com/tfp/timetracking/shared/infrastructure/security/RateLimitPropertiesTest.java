package com.tfp.timetracking.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tfp.timetracking.shared.infrastructure.security.RateLimitProperties.EndpointLimit;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

    @Test
    void rejectsANonSensibleGlobalLimit() {
        assertThatThrownBy(() -> new RateLimitProperties(0, Duration.ofMinutes(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(10, Duration.ZERO, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(10, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Perder el fichero de configuracion no debe desactivar el rate limiting en
     * silencio: sin reglas se cae al minimo historico (login y registro).
     */
    @Test
    void fallsBackToTheMinimumRuleSetWhenNoEndpointIsConfigured() {
        RateLimitProperties fromNull = new RateLimitProperties(10, Duration.ofMinutes(1), null);
        RateLimitProperties fromEmpty = new RateLimitProperties(10, Duration.ofMinutes(1), List.of());

        assertThat(fromNull.endpoints()).extracting(EndpointLimit::pattern)
                .containsExactly("/api/v1/auth/login", "/api/v1/auth/register");
        assertThat(fromEmpty.endpoints()).isEqualTo(fromNull.endpoints());
    }

    @Test
    void normalizesTheHttpMethodAndRejectsUnknownOnes() {
        assertThat(new EndpointLimit("post", "/x", null, null).method()).isEqualTo("POST");
        assertThat(new EndpointLimit(null, "/x", null, null).method()).isNull();
        assertThat(new EndpointLimit("  ", "/x", null, null).method()).isNull();

        assertThatThrownBy(() -> new EndpointLimit("FETCH", "/x", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRulesWithoutPatternOrWithNonSensibleOverrides() {
        assertThatThrownBy(() -> new EndpointLimit("POST", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EndpointLimit("POST", "  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EndpointLimit("POST", "/x", 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EndpointLimit("POST", "/x", null, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
