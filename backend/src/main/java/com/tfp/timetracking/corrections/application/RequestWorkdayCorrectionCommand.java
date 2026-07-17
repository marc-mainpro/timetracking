package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.corrections.domain.ProposedChanges;
import java.util.UUID;

public record RequestWorkdayCorrectionCommand(UUID workdayId, String reason, ProposedChanges proposedChanges) {}
