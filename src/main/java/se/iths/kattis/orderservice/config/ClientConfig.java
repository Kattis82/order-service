package se.iths.kattis.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// konfigurationsklass, skapar en RestClient-bean för anrop till product-service
// @Configuration = Spring läser klassen vid uppstart och registrerar beans
@Configuration
public class ClientConfig {

    // @Bean = Spring hanterar objektet och kan injicera det där det behövs
    // @Value hämtar base-url från application.properties eller miljövariabel
    @Bean
    public RestClient productRestClient(
            @Value("${product-service.base-url}") String baseUrl) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
