package se.iths.kattis.orderservice.dto;

// DTO som order-service skickar till product-service
// talar om vilken produkt och hur många som ska minska i lager
// likadan record behöver finnas i product-service med identiska fält
public record ProductStockRequest(Long productId, int quantity) {
}
