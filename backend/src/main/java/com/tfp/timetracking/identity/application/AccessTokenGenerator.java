package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.User;

public interface AccessTokenGenerator {

    IssuedAccessToken generate(User user);
}
