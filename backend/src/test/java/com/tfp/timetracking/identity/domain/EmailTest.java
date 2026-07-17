package com.tfp.timetracking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** Pruebas unitarias del VO Email (CONTEXT-DOMINIO §1, ficha T202). */
class EmailTest {

    @Test
    void normalizesToLowercaseAndTrims() {
        Email email = Email.of("  Jane.Doe@Example.COM  ");

        assertThat(email.value()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void rejectsNullEmail() {
        assertThatIllegalArgumentException().isThrownBy(() -> Email.of(null));
    }

    @Test
    void rejectsBlankEmail() {
        assertThatIllegalArgumentException().isThrownBy(() -> Email.of("   "));
    }

    @Test
    void rejectsEmailWithoutAtSign() {
        assertThatIllegalArgumentException().isThrownBy(() -> Email.of("not-an-email"));
    }

    @Test
    void rejectsEmailWithoutDomainDot() {
        assertThatIllegalArgumentException().isThrownBy(() -> Email.of("user@localhost"));
    }

    @Test
    void equalityIsBasedOnNormalizedValue() {
        Email first = Email.of("User@Example.com");
        Email second = Email.of("user@example.com");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    void toStringReturnsNormalizedValue() {
        assertThat(Email.of("User@Example.com")).hasToString("user@example.com");
    }
}
