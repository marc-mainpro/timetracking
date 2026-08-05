package com.tfp.timetracking.identity.domain;

public interface PasswordResetTokenGenerator {

    GeneratedPasswordResetToken generate();

    String hash(String rawToken);
}
