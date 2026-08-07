package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.Session;
import com.tfp.timetracking.identity.domain.SessionRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListSessionsUseCase {

    private final SessionRepository sessionRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public ListSessionsUseCase(SessionRepository sessionRepository, TenantContext tenantContext, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public List<Session> listCurrentUserSessions() {
        return sessionRepository.findByTenantIdAndUserId(tenantContext.currentTenantId(), tenantContext.currentUserId()).stream()
                .filter(session -> session.isActiveAt(clock.now()))
                .toList();
    }
}
