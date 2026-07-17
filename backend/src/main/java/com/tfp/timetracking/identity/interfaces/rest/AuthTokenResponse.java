package com.tfp.timetracking.identity.interfaces.rest;

import java.time.Instant;

public record AuthTokenResponse(String accessToken, Instant expiresAt) {}
