package com.tfp.timetracking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

import com.tfp.timetracking.notification.application.EmailDeliveryException;
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.NotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailSenderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationMetrics metrics = new NotificationMetrics(registry);
    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final RestClient restClient = restClientBuilder
            .baseUrl("https://api.resend.com")
            .defaultHeader("Authorization", "Bearer re_test_123")
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    private final ResendEmailSender sender = new ResendEmailSender(restClient, "no-reply@acme.test", metrics);

    @Test
    void sendsMessageThroughResendApi() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_123"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content()
                        .json(
                                """
                                {"from":"no-reply@acme.test","to":["empleado@acme.test"],"subject":"Recupera tu contrasena","text":"token-de-un-solo-uso"}
                                """))
                .andRespond(withNoContent());

        sender.send(new EmailMessage("empleado@acme.test", "Recupera tu contrasena", "token-de-un-solo-uso"));

        server.verify();
        assertThat(registry.get("notification.emails.sent").counter().count()).isEqualTo(1.0);
    }

    @Test
    void translatesResendApiFailureIntoDeliveryException() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("bad request").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> sender.send(new EmailMessage("a@acme.test", "asunto", "cuerpo")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("asunto");
        assertThat(registry.get("notification.emails.failed").counter().count()).isEqualTo(1.0);
    }
}
