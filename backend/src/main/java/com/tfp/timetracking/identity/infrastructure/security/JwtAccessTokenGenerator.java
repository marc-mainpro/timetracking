package com.tfp.timetracking.identity.infrastructure.security;

import com.tfp.timetracking.identity.application.AccessTokenGenerator;
import com.tfp.timetracking.identity.application.IssuedAccessToken;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.shared.domain.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenGenerator implements AccessTokenGenerator {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration accessTokenTtl;

    public JwtAccessTokenGenerator(
            JwtEncoder jwtEncoder, Clock clock, @Value("${auth.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public IssuedAccessToken generate(User user) {
        Instant issuedAt = clock.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("tenantId", user.tenantId().toString())
                .claim("roles", user.roles().stream().map(Enum::name).sorted().toList())
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}
