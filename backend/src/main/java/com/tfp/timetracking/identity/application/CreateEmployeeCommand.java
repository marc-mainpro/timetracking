package com.tfp.timetracking.identity.application;

import java.util.Set;

public record CreateEmployeeCommand(String email, String password, String firstName, String lastName, Set<String> roles) {}
