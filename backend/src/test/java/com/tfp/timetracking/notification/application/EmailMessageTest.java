package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailMessageTest {

    @Test
    void buildsMessageWithAllFields() {
        EmailMessage message = new EmailMessage("empleado@acme.test", "Verifica tu correo", "Tu código es 1234");

        assertThat(message.to()).isEqualTo("empleado@acme.test");
        assertThat(message.subject()).isEqualTo("Verifica tu correo");
        assertThat(message.body()).isEqualTo("Tu código es 1234");
    }

    @Test
    void rejectsMissingRecipient() {
        assertThatThrownBy(() -> new EmailMessage(null, "asunto", "cuerpo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destinatario");
        assertThatThrownBy(() -> new EmailMessage("  ", "asunto", "cuerpo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSubject() {
        assertThatThrownBy(() -> new EmailMessage("a@acme.test", " ", "cuerpo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asunto");
    }

    @Test
    void rejectsMissingBody() {
        assertThatThrownBy(() -> new EmailMessage("a@acme.test", "asunto", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cuerpo");
    }
}
