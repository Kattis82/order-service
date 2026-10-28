package se.iths.kattis.orderservice.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enkel endpoint för att kontrollera att tjänsten är igång
 */
// Kontrollerar att Order Service kan ta emot och besvara anrop.
@RestController
public class HealthController {
    // Returnerar ett enkelt meddelande när endpointen anropas.
    @GetMapping("/health")
    public String health() {
        return "Order Service is running!";
    }
}
