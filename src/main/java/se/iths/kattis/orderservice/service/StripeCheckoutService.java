package se.iths.kattis.orderservice.service;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.iths.kattis.orderservice.dto.OrderCheckoutResponse;
import se.iths.kattis.orderservice.model.Order;
import se.iths.kattis.orderservice.model.OrderItem;

@Service
public class StripeCheckoutService {

    // kopplingen mellan Order Service och Stripe
    private final StripeClient stripeClient;

    // hämtar Stripes hemliga API-nyckel från application.properties
    // och skapar en klient som kan kommunicera med Stripe
    public StripeCheckoutService(

            @Value("${stripe.secret-key}") String secretKey) {
        this.stripeClient = new StripeClient(secretKey);
    }


    // orderbaserade Stripe Checkout-prototypen, skapar en betalningssession
    // utifrån en sparad order med dess produkter, priser och antal

    // metoden hämtar namn, pris och antal från orderns sparade produktrader
    // omvandlar priset från kronor till öre, som Stripe förväntar sig för SEK
    // skickar med orderId som metadata i Stripe-sessionen, så att man senare kan
    // koppla betalningen till rätt order
    // returnerar order-ID, session-ID och Checkout-URL
    // metoden bekräftar inte att kunden har betalat
    public OrderCheckoutResponse createCheckoutSession(Order order)
            throws StripeException {

        SessionCreateParams.Builder paramsBuilder =
                SessionCreateParams.builder()
                        // anger att sessionen ska användas för betalning
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        // checkout-sessionen löper ut efter 30 minuter
                        .setExpiresAt(
                                java.time.Instant.now().plusSeconds(1860).getEpochSecond()
                        )
                        // Stripe skickar tillbaka kunden till frontend
                        // Session-ID:t läggs till efter lyckad betalning
                        .setSuccessUrl(
                                "http://localhost:5173/checkout/success"
                                        + "?session_id={CHECKOUT_SESSION_ID}")
                        // returadress om kunden avbryter betalningen
                        .setCancelUrl(
                                "http://localhost:5173/checkout/cancel")
                        // sparar orderns ID i Stripe-sessionens metadata
                        // webhooken kan senare använda det för att hitta ordern
                        .putMetadata("orderId", order.getId().toString());

        // går igenom orderns produktrader och lägger till dem
        // som separata artiklar i Stripe Checkout
        for (OrderItem item : order.getItems()) {

            // Omvandlar priset från kronor till öre
            // Exempel: 25,50 SEK blir 2550 öre
            long amountInOre = item.getPrice()
                    .movePointRight(2)
                    .longValueExact();

            paramsBuilder.addLineItem(
                    SessionCreateParams.LineItem.builder()
                            .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency("sek")
                                            .setUnitAmount(amountInOre)
                                            .setProductData(
                                                    SessionCreateParams.LineItem.PriceData.ProductData
                                                            .builder()
                                                            // visar produktens namn i Stripe Checkout
                                                            .setName(item.getName())
                                                            .build()
                                            )
                                            .build()
                            )
                            // anger hur många exemplar kunden ska betala för
                            .setQuantity((long) item.getQuantity())
                            .build()
            );
        }

        // skapar Checkout Session hos Stripe med alla orderns produkter
        Session session = stripeClient.v1()
                .checkout()
                .sessions()
                .create(paramsBuilder.build());

        // returnerar order-ID, Stripe-sessionens ID och betalningslänken
        // detta bekräftar inte att betalningen är genomförd
        // betalningsstatusen uppdateras senare via webhooken
        return new OrderCheckoutResponse(
                order.getId(),
                session.getId(),
                session.getUrl()
        );
    }


    // hämtar en befintlig Checkout-session från Stripe
    public Session getCheckoutSession(String sessionId)
            throws StripeException {

        return stripeClient.v1()
                .checkout()
                .sessions()
                .retrieve(sessionId);
    }


    // avslutar en öppen Checkout-session hos Stripe
    public Session expireCheckoutSession(String sessionId)
            throws StripeException {
        return stripeClient.v1()
                .checkout()
                .sessions()
                .expire(sessionId);
    }


}
