package se.iths.kattis.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// DTO som representerar en enskild produkt i ordern som skickas in
// innehåller vilket produkt-id som beställs och hur många
public record CreateOrderItemRequest(

        @NotNull(message = "productId får inte vara null")
        Long productId,

        // minst 1 måste beställas av varje produkt
        @Min(value = 1, message = "quantity måste vara minst 1")
        int quantity) {
}
