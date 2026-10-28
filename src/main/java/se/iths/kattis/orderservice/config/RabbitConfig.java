package se.iths.kattis.orderservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Konfigurerar RabbitMQ för Order Service: skapar kön och ställer in JSON-konvertering.
 */
@Configuration
public class RabbitConfig {

    // hämtar namnet på kön från application.properties, inte hårdkodas i koden
    @Value("${rabbitmq.queue.name}")
    private String queueName;


    /**
     * Skapar kön för orderbekräftelser om den inte redan finns. Kön är varaktig
     * och överlever en omstart av RabbitMQ.
     *
     * @return kön, med namn från {@code rabbitmq.queue.name}
     */
    // true = kön överlever en omstart
    @Bean
    public Queue orderQueue() {
        return new Queue(queueName, true);
    }


    /**
     * Konverterar mellan Java-objekt och JSON, så att {@code OrderResponse} kan skickas som JSON.
     *
     * @return JSON-konverteraren
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }


    /**
     * Skapar en {@link RabbitTemplate} som använder JSON-konverteraren.
     *
     * @param connectionFactory anslutningen till RabbitMQ
     * @return konfigurerad RabbitTemplate
     */
    // RabbitTemplate = Springs inbyggda klass för att skicka meddelanden till RabbitMQ
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
