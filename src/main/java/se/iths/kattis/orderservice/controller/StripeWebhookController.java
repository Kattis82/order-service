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
        this.webhookSecret = webhookSecret;
        this.orderService = orderService;
    }

    // tar emot webhook-anrop från Stripe
    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            // själva meddelandet från Stripe
            @RequestBody String payload,

            // Stripes signatur används för att kontrollera att anropet är äkta
            @RequestHeader("Stripe-Signature") String signature) {

        Event event;

        try {
            // kontrollerar Stripes signatur med den hemliga webhook-nyckeln
            // om signaturen inte stämmer ska meddelandet inte behandlas
            event = Webhook.constructEvent(
                    payload,
                    signature,
                    webhookSecret
            );

        } catch (SignatureVerificationException e) {
            // signaturen är felaktig
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ogiltig Stripe-signatur");

        } catch (Exception e) {
            // meddelandet kunde inte läsas eller verifieras
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ogiltig webhook-payload");
        }

        // vi behandlar bara händelser där Checkout-sessionen har slutförts
        // andra Stripe-händelser behöver inte hanteras här
        if (!"checkout.session.completed".equals(event.getType())) {
            return ResponseEntity.ok("Händelsen hanteras inte");
        }

        // hämtar informationen om Checkout-sessionen från Stripe-händelsen
        StripeObject stripeObject = event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        // kontrollerar att informationen verkligen är en Checkout Session
        if (!(stripeObject instanceof Session session)) {
            return ResponseEntity.badRequest()
                    .body("Kunde inte läsa Checkout Session");
        }

        // en slutförd Checkout-session betyder inte alltid att betalningen är klar
        // därför kontrollerar vi att Stripe anger statusen "paid"
        if (!"paid".equals(session.getPaymentStatus())) {
            return ResponseEntity.ok("Betalningen är inte genomförd");
        }

        // hämtar orderns ID från informationen vi sparade i Stripe-sessionen
        String orderIdValue = session.getMetadata() == null
                ? null
                : session.getMetadata().get("orderId");

        // om order-ID saknas kan vi inte koppla betalningen till en order
        if (orderIdValue == null) {
            return ResponseEntity.badRequest()
                    .body("Order-ID saknas i Stripe-sessionen");
        }

        try {
            // gör om order-ID från text till ett Long-tal
            Long orderId = Long.valueOf(orderIdValue);

            // ber OrderService kontrollera ordern och markera den som betald
            // OrderService kontrollerar också att Stripe-sessionen matchar ordern
            orderService.markOrderPaid(
                    orderId,
                    session.getId()
            );

            // meddelar Stripe att webhook-anropet har hanterats
            return ResponseEntity.ok("Ordern har hanterats");

        } catch (IllegalArgumentException e) {
            // order-ID:t var ogiltigt, ordern saknades
            // eller Stripe-sessionen matchade inte ordern
            return ResponseEntity.badRequest()
                    .body("Ordern eller Stripe-sessionen kunde inte verifieras");
        }
    }
}
