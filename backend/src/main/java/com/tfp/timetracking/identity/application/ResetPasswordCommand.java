package com.tfp.timetracking.identity.application;

public record ResetPasswordCommand(String token, String newPassword) {}
