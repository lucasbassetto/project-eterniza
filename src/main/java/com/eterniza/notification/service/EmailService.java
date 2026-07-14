package com.eterniza.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${eterniza.mail.from}") private String from;
    @Value("${eterniza.app.web-url}") private String webUrl;

    public void sendRevealEmail(String toEmail, String eventId, String eventName) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toEmail);
            msg.setSubject("Sua galeria Eterniza foi revelada!");
            msg.setText("""
                Olá!

                A galeria do evento "%s" está disponível agora.
                Todas as fotos dos seus convidados foram reveladas.

                Acesse aqui: %s/e/%s

                Eterniza — cada momento, para sempre.
                """.formatted(eventName, webUrl, eventId));
            mailSender.send(msg);
            log.info("E-mail enviado para {}", toEmail);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail para {}", toEmail, e);
        }
    }
}
