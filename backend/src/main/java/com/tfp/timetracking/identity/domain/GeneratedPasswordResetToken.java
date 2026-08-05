package com.tfp.timetracking.identity.domain;

public record GeneratedPasswordResetToken(String value, String hash) {

    public GeneratedPasswordResetToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El token es obligatorio");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("El hash del token es obligatorio");
        }
    }
}
