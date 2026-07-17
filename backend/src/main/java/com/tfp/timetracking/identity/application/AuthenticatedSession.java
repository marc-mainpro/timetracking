package com.tfp.timetracking.identity.application;

import java.time.Instant;

public record AuthenticatedSession(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}
