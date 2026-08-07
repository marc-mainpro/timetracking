package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.notification.application.NotificationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Implementacion por defecto del puerto {@link EmailSender} cuando no hay SMTP
 * configurado ({@code mail.enabled=false}, el valor por defecto).
 *
 * <p>Existe para que el arranque no dependa de tener un servidor de correo:
 * desarrollo local sin mailpit, y sobre todo la bateria de tests, funcionan sin
 * que ningun caso de uso tenga que preguntarse si el correo esta disponible.
 *
 * <p>Registra destinatario y asunto, nunca el cuerpo (RS-014). En consecuencia,
 * <b>un token enviado por este adaptador se pierde</b>: para probar flujos de
 * verificacion o recuperacion hay que usar un doble de test que capture los
 * mensajes, o levantar mailpit con {@code mail.enabled=true}.
 */
@Configuration
public class EmailSenderFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender loggingEmailSender(NotificationMetrics metrics) {
        return message -> {
            // Se cuenta como "skipped", no como fallo: no es un error, pero
            // tampoco es un envio, y confundir ambos casos hace que un entorno
            // mal configurado parezca sano (T140-04).
            metrics.recordSkipped();
            log.info(
                    "Correo NO enviado (mail.enabled=false). Destinatario: {}, asunto: '{}'",
                    message.to(),
                    message.subject());
        };
    }
}
