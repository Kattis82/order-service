package se.iths.kattis.orderservice.publisher;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.iths.kattis.orderservice.dto.OrderResponse;


/**
 * Skickar orderbekräftelser till RabbitMQ. Email Service lyssnar på samma kö
 * och skickar bekräftelsemejl till kunden.
 */
@Component
@RequiredArgsConstructor
public class OrderPublisher {

    // RabbitTemplate används för att skicka meddelanden till RabbitMQ
    private final RabbitTemplate rabbitTemplate;

    // hämtar namnet på kön från application.properties, inte hårdkodas i koden
    // email-service måste använda exakt samma könamn
    @Value("${rabbitmq.queue.name}")
    private String queueName;


    /**
     * Skickar en orderbekräftelse som JSON till kön. Könamnet hämtas från
     * {@code rabbitmq.queue.name} och måste vara detsamma i Email Service.
     *
     * @param orderResponse ordern som ska bekräftas, innehåller kundens e-postadress,
     *                      orderdatum, orderrader och totalpris
     */
    public void publishOrderConfirmation(OrderResponse orderResponse) {
        rabbitTemplate.convertAndSend(queueName, orderResponse);
    }

}
