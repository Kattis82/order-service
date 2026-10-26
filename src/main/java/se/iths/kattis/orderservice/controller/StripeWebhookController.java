package se.iths.kattis.orderservice.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.iths.kattis.orderservice.service.OrderService;

@RestController
// alla anrop till denna controller börjar med /webhooks/stripe
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private final String webhookSecret;
    private final OrderService orderService;

    // hämtar webhook-hemligheten från application.properties
    // och låter Spring koppla in OrderService
    public StripeWebhookController(
            @Value("${stripe.webhook-secret}") String webhookSecret,
            OrderService orderService) {
        this.webhookSecret = webhookSecret.replaceAll("\\s+", "");
        this.orderService = orderService;
    }

    // tar emot webhook-anrop från Stripe
    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            // själva meddelandet från Stripe, måste vara orörd text (inte DTO)
            // annars stämmer inte signaturen
            @RequestBody String payload,

            // Stripes signatur används för att kontrollera att anropet är äkta
            @RequestHeader("Stripe-Signature") String signature) {

        Event event;

        try {
            // kontrollerar signaturen med den hemliga webhook-nyckeln
            event = Webhook.constructEvent(payload, signature, webhookSecret);

        } catch (SignatureVerificationException e) {
            // felaktig signatur: behandla inte meddelandet
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ogiltig Stripe-signatur");

        } catch (Exception e) {
            // meddelandet kunde inte läsas
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ogiltig webhook-payload");
        }

        // vi hanterar bara genomförda och utgångna Checkout-sessioner
        // övriga händelser kvitteras med 200 så att Stripe inte skickar om dem
        if (!"checkout.session.completed".equals(event.getType())
                && !"checkout.session.expired".equals(event.getType())) {
            return ResponseEntity.ok("Händelsen hanteras inte");
        }

        // hämtar Checkout-sessionen från händelsen
        StripeObject stripeObject = event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        // fallback om Stripe-bibliotekets API-version inte matchar eventets
        if (stripeObject == null) {
            try {
                stripeObject = event.getDataObjectDeserializer()
                        .deserializeUnsafe();
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body("Kunde inte läsa Checkout Session");
            }
        }

        // kontrollerar att objektet verkligen är en Checkout Session
        // (skapar också variabeln "session" som används nedan)
        if (!(stripeObject instanceof Session session)) {
            return ResponseEntity.badRequest()
                    .body("Kunde inte läsa Checkout Session");
        }

        // hämtar orderns ID från metadata som sparades när sessionen skapades
        String orderIdValue = session.getMetadata() == null
                ? null
                : session.getMetadata().get("orderId");

        // permanent fel: omförsök hjälper inte, så svara 200 och logga
        if (orderIdValue == null) {
            System.err.println("Webhook ignorerad: order-ID saknas i sessionen "
                    + session.getId());
            return ResponseEntity.ok("Order-ID saknas, ignorerad");
        }

        try {
            Long orderId = Long.valueOf(orderIdValue);

            // betalning genomförd
            if ("checkout.session.completed".equals(event.getType())) {

                // kontrollera att betalningen verkligen är genomförd
                if (!"paid".equals(session.getPaymentStatus())) {
                    return ResponseEntity.ok("Betalningen är inte genomförd");
                }

                // markera ordern som betald och skicka orderbekräftelse
                orderService.markOrderPaid(orderId, session.getId());
                return ResponseEntity.ok("Ordern har hanterats");
            }

            // Checkout-sessionen har löpt ut: återställ lagret, markera ordern
            orderService.expirePendingOrder(orderId, session.getId());
            return ResponseEntity.ok("Utgången Checkout-session har hanterats");

        } catch (IllegalArgumentException e) {
            // permanent fel (ogiltigt id, order saknas, sessionen matchar inte)
            // svara 200 så att Stripe inte försöker om i flera dagar
            System.err.println("Webhook ignorerad: " + e.getMessage());
            return ResponseEntity.ok("Ignorerad");
        }
        // andra fel (t ex RabbitMQ eller Product Service nere) ger 500,
        // och då försöker Stripe skicka händelsen igen
    }
}
