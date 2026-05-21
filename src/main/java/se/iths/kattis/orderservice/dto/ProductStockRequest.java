package se.iths.kattis.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// DTO som beskriver en produkt i ordern
// används både i CreateOrderRequest och skickas vidare till product-service
// likadan record behöver finnas i product-service med identiska fält
public record ProductStockRequest(
        @NotNull(message = "productId får inte vara null")
        Long productId,
        @Min(value = 1, message = "quantity måste vara minst 1")
        int quantity) {
}
