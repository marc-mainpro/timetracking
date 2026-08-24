package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.notification.application.EmailDeliveryException;
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.notification.application.NotificationMetrics;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptador HTTP del puerto {@link EmailSender} usando la API de Resend. */
@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
@ConditionalOnProperty(name = "mail.provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient restClient;
    private final String from;
    private final NotificationMetrics metrics;

    @Autowired
    public ResendEmailSender(RestClient.Builder restClientBuilder, MailProperties mailProperties, NotificationMetrics metrics) {
        this(createRestClient(restClientBuilder, mailProperties.resend()), mailProperties.from(), metrics);
    }

    ResendEmailSender(RestClient restClient, String from, NotificationMetrics metrics) {
        this.restClient = restClient;
        this.from = from;
        this.metrics = metrics;
    }

    private static SimpleClientHttpRequestFactory requestFactory(MailProperties.Resend resend) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(resend.connectTimeout());
        requestFactory.setReadTimeout(resend.readTimeout());
        return requestFactory;
    }

    private static RestClient createRestClient(RestClient.Builder restClientBuilder, MailProperties.Resend resend) {
        return restClientBuilder
                .baseUrl(resend.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resend.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory(resend))
                .build();
    }

    @Override
    public void send(EmailMessage message) {
        try {
            restClient.post()
                    .uri("/emails")
                    .body(new ResendEmailRequest(from, message.to(), message.subject(), message.body()))
                    .retrieve()
                    .toBodilessEntity();
            metrics.recordSent();
            log.info("Correo enviado a {} con asunto '{}' via Resend", message.to(), message.subject());
        } catch (RestClientException e) {
            metrics.recordFailed();
            throw new EmailDeliveryException("No se pudo enviar el correo con asunto '" + message.subject() + "'", e);
        }
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String text) {

        private ResendEmailRequest(String from, String to, String subject, String text) {
            this(from, List.of(to), subject, text);
        }
    }
}
