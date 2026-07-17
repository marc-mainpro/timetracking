package com.tfp.timetracking.identity.application;

public interface RefreshTokenHasher {

    String hash(String rawToken);
}
