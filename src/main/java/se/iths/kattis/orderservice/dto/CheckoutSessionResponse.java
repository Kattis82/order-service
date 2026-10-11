package se.iths.kattis.orderservice.dto;

// det här är svaret som frontend eller Swagger får tillbaka: ett sessions-ID och
// adressen till Stripes betalningssida
public record CheckoutSessionResponse(
        String sessionId,
        String url
) {
}
