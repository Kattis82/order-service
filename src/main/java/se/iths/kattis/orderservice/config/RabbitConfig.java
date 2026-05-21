package se.iths.kattis.orderservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// konfigurerar RabbitMQ för order-service
// skapar kön och ställer in JSON-konvertering
@Configuration
public class RabbitConfig {

    // hämtar namnet på kön från application.properties, inte hårdkodas i koden
    @Value("${rabbitmq.queue.name}")
    private String queueName;


    // skapar kön i RabbitMQ om den inte redan finns
    // true = kön överlever en omstart av RabbitMQ (durable)
    @Bean
    public Queue orderQueue() {
        return new Queue(queueName, true);
    }

    // konverterar mellan Java-objekt och JSON automatiskt
    // behövs för att RabbitMQ ska kunna skicka OrderResponse som JSON
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // konfigurerar RabbitTemplate med JSON-konverteraren
    // RabbitTemplate = Springs inbyggda klass för att skicka meddelanden till RabbitMQ
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
