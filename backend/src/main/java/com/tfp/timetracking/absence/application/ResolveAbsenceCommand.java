package com.tfp.timetracking.absence.application;

import java.util.UUID;

public record ResolveAbsenceCommand(UUID absenceRequestId, String resolutionComment) {}
