package se.iths.kattis.orderservice.dto;

// orderId identifierar ordern i vår databas
// sessionId identifierar Checkout-sessionen hos Stripe
// url är adressen som frontend senare kan skicka kunden till
public record OrderCheckoutResponse(
        Long orderId,
        String sessionId,
        String url
) {
}
