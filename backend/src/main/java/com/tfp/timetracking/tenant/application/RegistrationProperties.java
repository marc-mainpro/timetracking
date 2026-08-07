package com.tfp.timetracking.tenant.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del registro público (T53-04, RF-TEN-010, RF-REG-003), enlazada
 * desde {@code config/registration.yml} bajo el prefijo {@code registration.*}.
 *
 * <p>La bandera maestra {@code registration.public.enabled} (RF-TEN-010) NO se
 * enlaza aquí: {@code public} es una palabra reservada de Java y no puede ser el
 * nombre de un componente del record. Se lee con {@code @Value} allí donde se
 * usa, igual que hacía ya {@code AuthRegisterController}.
 *
 * @param verification parámetros del token de verificación de correo
 * @param throttle límites anti-abuso por IP y por correo
 * @param verificationUrlTemplate plantilla de la URL de verificación que se
 *     incluye en el correo; {@code %s} se sustituye por el token
 * @param ipHashPepper secreto usado para que el hash de IP no sea invertible
 *     por fuerza bruta (ver {@code Sha256IpHasher}); vacío = aleatorio al
 *     arrancar
 */
@ConfigurationProperties(prefix = "registration")
public record RegistrationProperties(
        @DefaultValue Verification verification,
        @DefaultValue Throttle throttle,
        @DefaultValue("http://localhost:4200/registro/verificar?token=%s") String verificationUrlTemplate,
        @DefaultValue("") String ipHashPepper) {

    /**
     * @param tokenTtl validez del token de verificación
     * @param maxResends número máximo de reenvíos por solicitud
     */
    public record Verification(@DefaultValue("PT24H") Duration tokenTtl, @DefaultValue("3") int maxResends) {}

    /**
     * @param window ventana deslizante en la que se cuentan las solicitudes
     * @param maxPerIp solicitudes máximas por IP dentro de la ventana
     * @param maxPerEmail solicitudes máximas por correo dentro de la ventana
     */
    public record Throttle(
            @DefaultValue("PT1H") Duration window,
            @DefaultValue("5") int maxPerIp,
            @DefaultValue("3") int maxPerEmail) {}
}
