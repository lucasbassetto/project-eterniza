---
name: Fase 7 — Pacote notification
description: Email notifications for event reveal via RabbitMQ consumer
---

## Overview

Fase 7 implements the notification system that consumes reveal events from RabbitMQ and sends confirmation emails to hosts when their event's photo gallery is revealed.

## Acceptance Criteria

### NOTIF-01: Email notification on reveal
- RevealNotificationConsumer listens to REVEAL_QUEUE
- Receives message with eventId and hostId
- Looks up Event by eventId and Host by hostId
- If either missing: logs warning, returns silently
- If found: calls EmailService.sendRevealEmail()
- Email subject: "Sua galeria Eterniza foi revelada!"
- Email body includes event name and gallery access link: `{webUrl}/e/{eventId}`

### NOTIF-02: Email service reliability
- EmailService sends SimpleMailMessage via JavaMailSender
- Configuration via @Value: eterniza.mail.from, eterniza.app.web-url
- On send failure: logs error but doesn't crash (exception caught)
- Returns void (async, fire-and-forget)

## Dependencies

- Fase 5: Event entity, EventRepository
- Fase 3: Host entity, HostRepository
- RabbitMQConfig: REVEAL_QUEUE constant
- Spring Mail: JavaMailSender auto-configured via application.yml

## Test Coverage

- **EmailServiceTest**: sends mail, handles exceptions gracefully
- **RevealNotificationConsumerTest**: valid event+host flow, missing event, missing host
