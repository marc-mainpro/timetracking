package com.tfp.timetracking.identity.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object Email (CONTEXT-DOMINIO §1, agregado User): valida formato y
 * normaliza a minusculas. Dos {@code Email} son iguales si su valor
 * normalizado coincide.
 */
public final class Email {

    private static final Pattern SIMPLE_EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        String normalized = rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SIMPLE_EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email invalido: " + rawEmail);
        }
        return new Email(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Email email)) {
            return false;
        }
        return value.equals(email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
