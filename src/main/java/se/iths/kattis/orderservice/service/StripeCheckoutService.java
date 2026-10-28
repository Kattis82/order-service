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

/**
 * Kommunicerar med Stripe för att skapa och hämta Checkout-sessioner.
 */
@Service
public class StripeCheckoutService {

    // kopplingen mellan Order Service och Stripe
    private final StripeClient stripeClient;

    // frontendens adress, hämtad från application.properties
    private final String frontendUrl;

    // hämtar Stripes hemliga API-nyckel från application.properties
    // och skapar en klient som kan kommunicera med Stripe
    public StripeCheckoutService(

            @Value("${stripe.secret-key}") String secretKey, @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.stripeClient = new StripeClient(secretKey.replaceAll("\\s+", ""));
        this.frontendUrl = frontendUrl;
    }


    /**
     * Skapar en Stripe Checkout-session för en order. Varje orderrad blir en artikel
     * med pris i öre (SEK), och orderns id sparas som metadata så att webhooken kan
     * koppla betalningen till ordern. Sessionen löper ut efter cirka 30 minuter.
     * Metoden bekräftar inte att kunden har betalat.
     *
     * @param order ordern med sina orderrader
     * @return order-id, session-id och betalningslänk
     * @throws StripeException om Stripe nekar eller inte kan nås
     */
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
                                frontendUrl + "/checkout/success"
                                        + "?session_id={CHECKOUT_SESSION_ID}")
                        // returadress om kunden avbryter betalningen
                        .setCancelUrl(
                                frontendUrl + "/checkout/cancel")
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


    /**
     * Hämtar en befintlig Checkout-session från Stripe.
     *
     * @param sessionId Stripe-sessionens id
     * @return sessionen
     * @throws StripeException om sessionen inte kan hämtas
     */
    public Session getCheckoutSession(String sessionId)
            throws StripeException {

        return stripeClient.v1()
                .checkout()
                .sessions()
                .retrieve(sessionId);
    }


    /**
     * Avslutar en öppen Checkout-session hos Stripe, så att kunden inte längre kan betala.
     * Stripe skickar då händelsen {@code checkout.session.expired}.
     *
     * @param sessionId Stripe-sessionens id
     * @return den avslutade sessionen
     * @throws StripeException om sessionen inte kan avslutas, till exempel om den redan är slutförd
     */
    public Session expireCheckoutSession(String sessionId)
            throws StripeException {
        return stripeClient.v1()
                .checkout()
                .sessions()
                .expire(sessionId);
    }


}
