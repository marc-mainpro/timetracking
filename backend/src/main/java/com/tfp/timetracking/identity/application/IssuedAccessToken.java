package com.tfp.timetracking.identity.application;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {}
