package se.iths.kattis.orderservice.service;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.iths.kattis.orderservice.dto.CheckoutSessionResponse;
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

    // första prototypen med hårdkodad testprodukt
    
    // skapar en enkel Checkout Session med en testprodukt för 10 SEK
    // används för att testa Stripe utan att koppla betalningen till en order
    public CheckoutSessionResponse createTestCheckoutSession()
            throws StripeException {

        // FE-31-prototyp: en testprodukt för 10 SEK
        // detta är inte kopplat till en riktig order ännu
        // SessionCreateParams beskriver hur betalningen ska se ut, t ex produkter, priser och returadresser
        SessionCreateParams params = SessionCreateParams.builder()
                // PAYMENT betyder att kunden ska genomföra en betalning
                .setMode(SessionCreateParams.Mode.PAYMENT)
                // kunden skickas tillbaka till frontend efter lyckad
                // betalning eller om betalningen avbryts
                .setSuccessUrl("http://localhost:5173/")
                .setCancelUrl("http://localhost:5173/")
                // lägger till en testprodukt som kostar 10 SEK
                // Stripe förväntar sig beloppet i öre: 10 SEK = 1000 öre
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("sek")
                                                .setUnitAmount(1000L)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData
                                                                .builder()
                                                                .setName("FE-31 testprodukt")
                                                                .build()
                                                )
                                                .build()
                                )
                                .setQuantity(1L)
                                .build()
                )
                .build();

        // skapar betalningssessionen hos Stripe
        // att sessionen finns betyder inte att kunden har betalat
        Session session = stripeClient.v1()
                .checkout()
                .sessions()
                .create(params);

        // returnerar sessionens ID och URL till anroparen
        return new CheckoutSessionResponse(
                session.getId(),
                session.getUrl()
        );
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


}
