package com.tfp.timetracking.identity.infrastructure.security;

import com.tfp.timetracking.identity.application.RefreshTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OpaqueRefreshTokenGenerator implements RefreshTokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
