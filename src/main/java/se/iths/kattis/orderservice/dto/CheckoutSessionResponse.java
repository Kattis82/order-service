package se.iths.kattis.orderservice.dto;

/**
 * Uppgifter om en Stripe Checkout-session.
 */
// det här är svaret som frontend eller Swagger får tillbaka: ett sessions-ID och
// adressen till Stripes betalningssida
public record CheckoutSessionResponse(
        String sessionId,
        String url
) {
}
