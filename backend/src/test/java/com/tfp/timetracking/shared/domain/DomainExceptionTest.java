package com.tfp.timetracking.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void exposesErrorCodeAndMessage() {
        TestDomainException exception = new TestDomainException("TEST_ERROR", "Regla incumplida");

        assertThat(exception.errorCode()).isEqualTo("TEST_ERROR");
        assertThat(exception).hasMessage("Regla incumplida");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void preservesCauseWhenProvided() {
        IllegalStateException cause = new IllegalStateException("boom");
        TestDomainException exception = new TestDomainException("TEST_ERROR", "Regla incumplida", cause);

        assertThat(exception.errorCode()).isEqualTo("TEST_ERROR");
        assertThat(exception).hasMessage("Regla incumplida");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    private static final class TestDomainException extends DomainException {

        private TestDomainException(String errorCode, String message) {
            super(errorCode, message);
        }

        private TestDomainException(String errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }
}
