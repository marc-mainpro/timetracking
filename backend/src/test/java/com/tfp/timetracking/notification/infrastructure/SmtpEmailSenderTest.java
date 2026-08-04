package com.tfp.timetracking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tfp.timetracking.notification.application.EmailDeliveryException;
import com.tfp.timetracking.notification.application.EmailMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(javaMailSender, "no-reply@acme.test");

    @Test
    void sendsMessageWithConfiguredSender() {
        sender.send(new EmailMessage("empleado@acme.test", "Recupera tu contraseña", "token-de-un-solo-uso"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("no-reply@acme.test");
        assertThat(sent.getTo()).containsExactly("empleado@acme.test");
        assertThat(sent.getSubject()).isEqualTo("Recupera tu contraseña");
        assertThat(sent.getText()).isEqualTo("token-de-un-solo-uso");
    }

    @Test
    void translatesTransportFailureIntoDeliveryException() {
        doThrow(new MailSendException("SMTP caído")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.send(new EmailMessage("a@acme.test", "asunto", "cuerpo")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("asunto");
    }
}
