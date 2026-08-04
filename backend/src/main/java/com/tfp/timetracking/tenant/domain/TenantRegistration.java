package com.tfp.timetracking.tenant.domain;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationApproved;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationEmailVerified;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRejected;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRequested;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationVerificationRequested;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raíz {@code TenantRegistration} (T53-01, diseño §7.3): la solicitud
 * de alta pública de una organización, <b>separada del tenant</b>.
 *
 * <p>Existe porque el alta pública y la creación de un tenant son dos hechos
 * distintos: cualquiera puede solicitar, pero solo la plataforma decide, y el
 * tenant resultante nace en {@code PENDING}, nunca {@code ACTIVE} (T53-03).
 * Mientras la solicitud vive, no hay tenant, no hay usuario y no hay nada que
 * un atacante pueda usar más allá de la propia fila.
 *
 * <p>Modelo de dominio puro: sin Spring ni JPA. {@code Clock}, {@code IdGenerator}
 * y {@link VerificationTokenGenerator} entran como parámetros de método.
 *
 * <p>Todas las transiciones válidas e inválidas se deciden aquí y solo aquí; el
 * controlador nunca comprueba estados.
 */
public final class TenantRegistration {

    private static final int MAX_TEXT_LENGTH = 200;

    private final UUID id;
    private final String companyName;
    private final String ownerFirstName;
    private final String ownerLastName;
    private final String email;
    private final String ownerPasswordHash;
    private final String timezone;
    private TenantRegistrationStatus status;
    private String verificationTokenHash;
    private Instant verificationTokenExpiresAt;
    private Instant verificationSentAt;
    private int resendCount;
    private final String source;
    private final String ipHash;
    private String decisionReason;
    private UUID createdTenantId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant verifiedAt;
    private Instant decidedAt;

    private final List<Object> domainEvents = new ArrayList<>();

    private TenantRegistration(
            UUID id,
            String companyName,
            String ownerFirstName,
            String ownerLastName,
            String email,
            String ownerPasswordHash,
            String timezone,
            TenantRegistrationStatus status,
            String verificationTokenHash,
            Instant verificationTokenExpiresAt,
            Instant verificationSentAt,
            int resendCount,
            String source,
            String ipHash,
            String decisionReason,
            UUID createdTenantId,
            Instant createdAt,
            Instant updatedAt,
            Instant verifiedAt,
            Instant decidedAt) {
        this.id = id;
        this.companyName = companyName;
        this.ownerFirstName = ownerFirstName;
        this.ownerLastName = ownerLastName;
        this.email = email;
        this.ownerPasswordHash = ownerPasswordHash;
        this.timezone = timezone;
        this.status = status;
        this.verificationTokenHash = verificationTokenHash;
        this.verificationTokenExpiresAt = verificationTokenExpiresAt;
        this.verificationSentAt = verificationSentAt;
        this.resendCount = resendCount;
        this.source = source;
        this.ipHash = ipHash;
        this.decisionReason = decisionReason;
        this.createdTenantId = createdTenantId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.verifiedAt = verifiedAt;
        this.decidedAt = decidedAt;
    }

    /**
     * Factoría: registra una nueva solicitud en
     * {@code PENDING_EMAIL_VERIFICATION} y genera su primer token de
     * verificación. Emite {@link TenantRegistrationRequested} y
     * {@link TenantRegistrationVerificationRequested}.
     *
     * @param ownerPasswordHash contraseña <b>ya hasheada</b>; el dominio de
     *     tenant nunca ve una contraseña en claro
     * @param ipHash huella de la IP de origen (RF-REG-003); nunca la IP en claro
     */
    public static TenantRegistration request(
            String companyName,
            String ownerFirstName,
            String ownerLastName,
            String email,
            String ownerPasswordHash,
            String timezone,
            String source,
            String ipHash,
            Duration tokenTtl,
            Clock clock,
            IdGenerator idGenerator,
            VerificationTokenGenerator tokenGenerator) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        Objects.requireNonNull(tokenGenerator, "tokenGenerator no puede ser null");
        Objects.requireNonNull(tokenTtl, "tokenTtl no puede ser null");
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("La caducidad del token debe ser positiva");
        }

        String validatedCompany = validateText(companyName, "El nombre de la organización es obligatorio");
        String validatedFirstName = validateText(ownerFirstName, "El nombre del propietario es obligatorio");
        String validatedLastName = validateText(ownerLastName, "Los apellidos del propietario son obligatorios");
        String normalizedEmail = validateEmail(email);
        String validatedHash = validateText(ownerPasswordHash, "El hash de la contraseña es obligatorio");
        String validatedTimezone = validateTimezone(timezone);
        String validatedSource = validateText(source, "La fuente de la solicitud es obligatoria");

        Instant now = clock.now();
        VerificationToken token = tokenGenerator.generate();
        TenantRegistration registration = new TenantRegistration(
                idGenerator.newId(),
                validatedCompany,
                validatedFirstName,
                validatedLastName,
                normalizedEmail,
                validatedHash,
                validatedTimezone,
                TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION,
                token.hash(),
                now.plus(tokenTtl),
                now,
                0,
                validatedSource,
                ipHash == null || ipHash.isBlank() ? null : ipHash,
                null,
                null,
                now,
                now,
                null,
                null);
        registration.domainEvents.add(new TenantRegistrationRequested(
                idGenerator.newId(), now, registration.id, validatedCompany, normalizedEmail, validatedSource));
        registration.domainEvents.add(new TenantRegistrationVerificationRequested(
                idGenerator.newId(),
                now,
                registration.id,
                normalizedEmail,
                validatedFirstName,
                token.value(),
                registration.verificationTokenExpiresAt,
                false));
        return registration;
    }

    /** Reconstruye una solicitud existente desde persistencia. No genera eventos. */
    public static TenantRegistration reconstitute(
            UUID id,
            String companyName,
            String ownerFirstName,
            String ownerLastName,
            String email,
            String ownerPasswordHash,
            String timezone,
            TenantRegistrationStatus status,
            String verificationTokenHash,
            Instant verificationTokenExpiresAt,
            Instant verificationSentAt,
            int resendCount,
            String source,
            String ipHash,
            String decisionReason,
            UUID createdTenantId,
            Instant createdAt,
            Instant updatedAt,
            Instant verifiedAt,
            Instant decidedAt) {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        Objects.requireNonNull(createdAt, "createdAt no puede ser null");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser null");
        return new TenantRegistration(
                id,
                companyName,
                ownerFirstName,
                ownerLastName,
                email,
                ownerPasswordHash,
                timezone,
                status,
                verificationTokenHash,
                verificationTokenExpiresAt,
                verificationSentAt,
                resendCount,
                source,
                ipHash,
                decisionReason,
                createdTenantId,
                createdAt,
                updatedAt,
                verifiedAt,
                decidedAt);
    }

    /**
     * Verifica el correo consumiendo el token: la solicitud pasa a
     * {@code PENDING_REVIEW} y el hash se borra, de modo que el token es
     * irrepetible aunque el enlace se reenvíe (T53-05: un solo uso).
     *
     * @throws InvalidVerificationTokenException si la solicitud ya no está
     *     pendiente de verificación, si el token no coincide o si ha caducado
     */
    public void verifyEmail(String rawToken, VerificationTokenGenerator tokenGenerator, Clock clock, IdGenerator idGenerator) {
        Objects.requireNonNull(tokenGenerator, "tokenGenerator no puede ser null");
        Objects.requireNonNull(idGenerator, "idGenerator no puede ser null");
        if (status != TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION) {
            throw new InvalidVerificationTokenException();
        }
        if (rawToken == null || rawToken.isBlank() || verificationTokenHash == null) {
            throw new InvalidVerificationTokenException();
        }
        if (!verificationTokenHash.equals(tokenGenerator.hash(rawToken))) {
            throw new InvalidVerificationTokenException();
        }
        Instant now = requireNow(clock);
        if (verificationTokenExpiresAt != null && !now.isBefore(verificationTokenExpiresAt)) {
            throw new InvalidVerificationTokenException();
        }
        this.status = TenantRegistrationStatus.PENDING_REVIEW;
        this.verificationTokenHash = null;
        this.verificationTokenExpiresAt = null;
        this.verifiedAt = now;
        this.updatedAt = now;
        this.domainEvents.add(new TenantRegistrationEmailVerified(idGenerator.newId(), now, id, email));
    }

    /**
     * Genera un token nuevo y pide que se reenvíe el correo de verificación
     * (T53-05). Invalida el token anterior: como mucho hay un token vivo por
     * solicitud.
     *
     * @throws IllegalTenantRegistrationTransitionException si la solicitud ya
     *     no está pendiente de verificación
     * @throws VerificationResendLimitExceededException si se agotaron los
     *     reenvíos permitidos
     */
    public void resendVerification(
            int maxResends,
            Duration tokenTtl,
            Clock clock,
            IdGenerator idGenerator,
            VerificationTokenGenerator tokenGenerator) {
        if (status != TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION) {
            throw new IllegalTenantRegistrationTransitionException(status, "reenviar la verificación");
        }
        if (resendCount >= maxResends) {
            throw new VerificationResendLimitExceededException(maxResends);
        }
        Instant now = requireNow(clock);
        VerificationToken token = tokenGenerator.generate();
        this.verificationTokenHash = token.hash();
        this.verificationTokenExpiresAt = now.plus(tokenTtl);
        this.verificationSentAt = now;
        this.resendCount = resendCount + 1;
        this.updatedAt = now;
        this.domainEvents.add(new TenantRegistrationVerificationRequested(
                idGenerator.newId(),
                now,
                id,
                email,
                ownerFirstName,
                token.value(),
                verificationTokenExpiresAt,
                true));
    }

    /**
     * Marca la solicitud como caducada. Solo tiene sentido mientras espera
     * verificación: una solicitud ya verificada no caduca por el token.
     */
    public void expire(Clock clock) {
        if (status != TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION) {
            throw new IllegalTenantRegistrationTransitionException(status, "caducar");
        }
        Instant now = requireNow(clock);
        this.status = TenantRegistrationStatus.EXPIRED;
        this.verificationTokenHash = null;
        this.verificationTokenExpiresAt = null;
        this.updatedAt = now;
    }

    /**
     * Aprueba la solicitud (solo desde {@code PENDING_REVIEW}). No crea el
     * tenant: eso lo hace el caso de uso, en la misma transacción, y lo
     * confirma con {@link #markConsumed}.
     */
    public void approve(Clock clock) {
        requireStatus(TenantRegistrationStatus.PENDING_REVIEW, "aprobar");
        Instant now = requireNow(clock);
        this.status = TenantRegistrationStatus.APPROVED;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    /**
     * Registra que la solicitud aprobada ya se materializó en un tenant y su
     * propietario. Estado terminal: una segunda aprobación no puede volver a
     * crear un tenant (T53-03, idempotencia).
     */
    public void markConsumed(UUID tenantId, UUID ownerUserId, Clock clock, IdGenerator idGenerator) {
        requireStatus(TenantRegistrationStatus.APPROVED, "consumir");
        Objects.requireNonNull(tenantId, "tenantId no puede ser null");
        Objects.requireNonNull(ownerUserId, "ownerUserId no puede ser null");
        Instant now = requireNow(clock);
        this.status = TenantRegistrationStatus.CONSUMED;
        this.createdTenantId = tenantId;
        this.updatedAt = now;
        this.domainEvents.add(new TenantRegistrationApproved(idGenerator.newId(), now, id, tenantId, ownerUserId));
    }

    /** Rechaza la solicitud con motivo obligatorio (solo desde {@code PENDING_REVIEW}). */
    public void reject(String reason, Clock clock, IdGenerator idGenerator) {
        requireStatus(TenantRegistrationStatus.PENDING_REVIEW, "rechazar");
        String validatedReason = validateText(reason, "El motivo del rechazo es obligatorio");
        Instant now = requireNow(clock);
        this.status = TenantRegistrationStatus.REJECTED;
        this.decisionReason = validatedReason;
        this.decidedAt = now;
        this.updatedAt = now;
        this.verificationTokenHash = null;
        this.verificationTokenExpiresAt = null;
        this.domainEvents.add(new TenantRegistrationRejected(idGenerator.newId(), now, id, validatedReason));
    }

    /** Indica si el token de verificación ha caducado a fecha {@code now}. */
    public boolean isVerificationExpiredAt(Instant now) {
        return status == TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION
                && verificationTokenExpiresAt != null
                && !now.isBefore(verificationTokenExpiresAt);
    }

    /** Devuelve y limpia los eventos de dominio acumulados por el agregado. */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void requireStatus(TenantRegistrationStatus required, String transition) {
        if (status != required) {
            throw new IllegalTenantRegistrationTransitionException(status, transition);
        }
    }

    private static Instant requireNow(Clock clock) {
        Objects.requireNonNull(clock, "clock no puede ser null");
        return clock.now();
    }

    private static String validateText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(message + " (máximo " + MAX_TEXT_LENGTH + " caracteres)");
        }
        return trimmed;
    }

    private static String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Email invalido");
        }
        return normalized;
    }

    private static String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("La zona horaria es obligatoria");
        }
        try {
            ZoneId.of(timezone);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("Zona horaria IANA invalida: " + timezone, e);
        }
        return timezone;
    }

    public UUID id() {
        return id;
    }

    public String companyName() {
        return companyName;
    }

    public String ownerFirstName() {
        return ownerFirstName;
    }

    public String ownerLastName() {
        return ownerLastName;
    }

    public String email() {
        return email;
    }

    public String ownerPasswordHash() {
        return ownerPasswordHash;
    }

    public String timezone() {
        return timezone;
    }

    public TenantRegistrationStatus status() {
        return status;
    }

    public String verificationTokenHash() {
        return verificationTokenHash;
    }

    public Instant verificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    public Instant verificationSentAt() {
        return verificationSentAt;
    }

    public int resendCount() {
        return resendCount;
    }

    public String source() {
        return source;
    }

    public String ipHash() {
        return ipHash;
    }

    public String decisionReason() {
        return decisionReason;
    }

    public UUID createdTenantId() {
        return createdTenantId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
