package com.tfp.timetracking.identity.interfaces.rest;

import com.tfp.timetracking.identity.domain.Session;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduce el agregado {@link Session} a su DTO de respuesta.
 *
 * <p>Existe porque el mapeo estaba en el propio {@code SessionController}, y un
 * controlador no puede conocer tipos de dominio: la excepcion por convencion de
 * ADR-0011 alcanza solo a las clases {@code *RestMapper}, que son traduccion de
 * borde, no logica de negocio. Con el mapeo dentro del controlador se rompian
 * las reglas de {@code LayeredArchitectureTest}.
 */
@Component
public class SessionRestMapper {

    public List<SessionResponse> toResponses(List<Session> sessions, UUID currentSessionId) {
        return sessions.stream().map(session -> toResponse(session, currentSessionId)).toList();
    }

    public SessionResponse toResponse(Session session, UUID currentSessionId) {
        return new SessionResponse(
                session.id(),
                session.createdAt(),
                session.lastUsedAt(),
                session.expiresAt(),
                session.revokedAt(),
                session.id().equals(currentSessionId));
    }
}
