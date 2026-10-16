package se.iths.kattis.orderservice.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Kontrollerar att Order Service kan ta emot och besvara anrop.
@RestController
public class HealthController {
    // Returnerar ett enkelt meddelande när endpointen anropas.
    @GetMapping("/health")
    public String health() {
        return "Order Service is running!";
    }
}
