package org.example.notifyservice.consumer;

import org.example.notifyservice.service.EmailService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedConsumer {

    private final EmailService emailService;

    public OrderCreatedConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "${notification.kafka.order-created-topic}")
    public void consume(String email) {
        if (email != null && !email.isBlank()) {
            emailService.sendOrderCreatedEmail(email);
        }
    }
}
