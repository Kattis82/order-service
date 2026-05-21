package se.iths.kattis.orderservice.publisher;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.iths.kattis.orderservice.dto.OrderResponse;

// skickar orderbekräftelse till RabbitMQ-kön efter att en order skapats
// email-service lyssnar på samma kö och skickar bekräftelsemail till kunden
@Component
@RequiredArgsConstructor
public class OrderPublisher {

    // RabbitTemplate används för att skicka meddelanden till RabbitMQ
    private final RabbitTemplate rabbitTemplate;

    // hämtar namnet på kön från application.properties, inte hårdkodas i koden
    // email-service måste använda exakt samma könamn
    @Value("${rabbitmq.queue.name}")
    private String queueName;


    // skickar OrderResponse till kön som JSON
    // OrderResponse innehåller customerName (mejladressen), orderDate, items och totalPrice
    public void publishOrderConfirmation(OrderResponse orderResponse) {
        rabbitTemplate.convertAndSend(queueName, orderResponse);
    }


}
