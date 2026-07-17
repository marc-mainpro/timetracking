package com.tfp.timetracking.identity.application;

public record AuthenticateUserCommand(String email, String password) {}
