package com.eterniza.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class EmailServiceTest {

    private EmailService emailService;

    @Mock private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "from", "no-reply@eterniza.com");
        ReflectionTestUtils.setField(emailService, "webUrl", "http://localhost:3000");
    }

    @Test
    void sendRevealEmail_validParams_sendsMail() {
        String toEmail = "host@example.com";
        String eventId = "event-123";
        String eventName = "Festa de Aniversário";

        emailService.sendRevealEmail(toEmail, eventId, eventName);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendRevealEmail_exceptionOnSend_loggedNotThrown() {
        String toEmail = "host@example.com";
        String eventId = "event-123";
        String eventName = "Festa";

        org.mockito.Mockito.doThrow(new RuntimeException("Mail server down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw
        emailService.sendRevealEmail(toEmail, eventId, eventName);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
