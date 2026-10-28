package se.iths.kattis.orderservice.dto;

/**
 * Svar efter checkout: order-id, Stripe-session-id och betalningslänk.
 */
// orderId identifierar ordern i vår databas
// sessionId identifierar Checkout-sessionen hos Stripe
// url är adressen som frontend senare kan skicka kunden till
public record OrderCheckoutResponse(
        Long orderId,
        String sessionId,
        String url
) {
}
