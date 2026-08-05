package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.User;
import java.util.UUID;

public interface AccessTokenGenerator {

    IssuedAccessToken generate(User user, UUID sessionId);
}
