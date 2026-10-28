package se.iths.kattis.orderservice.controller;

import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import se.iths.kattis.orderservice.dto.CreateOrderRequest;
import se.iths.kattis.orderservice.dto.OrderCheckoutResponse;
import se.iths.kattis.orderservice.dto.OrderResponse;
import se.iths.kattis.orderservice.service.OrderService;
import se.iths.kattis.orderservice.service.StripeCheckoutService;

import java.util.List;

/**
 * Tar emot anrop för ordrar. Alla endpoints nås via {@code /orders}
 * och kräver en giltig JWT.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final StripeCheckoutService stripeCheckoutService;


    /**
     * Skapar en ny order utan betalning. Lagret minskas i Product Service
     * och en orderbekräftelse skickas direkt.
     *
     * @param orderRequest produkter och antal som ska beställas
     * @param jwt          den validerade JWT-token, kundens e-postadress hämtas från subject
     * @return den skapade ordern med status 201 Created
     */
    // @ResponseStatus = för att swagger ska förstå att 201 förväntas
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            // @Valid aktiverar valideringen på CreateOrderRequest
            // @AuthenticationPrincipal Jwt jwt = ger tillgång till JWT-token
            @Valid @RequestBody CreateOrderRequest orderRequest,
            @AuthenticationPrincipal Jwt jwt) {

        // hämtar bearer-token, ska skicka vidare till product-service
        String bearerToken = "Bearer " + jwt.getTokenValue();

        // hämtar kundens mailadress från JWT, används som customerName i ordern
        String customerName = jwt.getSubject();

        OrderResponse orderResponse = orderService.createOrder(orderRequest, bearerToken, customerName);

        // // 201 när ny resurs skapats
        return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse);
    }

    /**
     * Skapar en order med status PENDING och startar en Stripe Checkout-session.
     *
     * @param orderRequest produkter och antal som ska beställas
     * @param jwt          den validerade JWT-token för den inloggade kunden
     * @return order-id, Stripe-session-id och betalningslänk
     * @throws StripeException om Stripe-sessionen inte kan skapas
     */
    @PostMapping("/checkout")
    public ResponseEntity<OrderCheckoutResponse> createCheckoutOrder(
            @Valid @RequestBody CreateOrderRequest orderRequest,
            @AuthenticationPrincipal Jwt jwt) throws StripeException {

        // skickar JWT vidare så att Product Service kan autentisera anropet
        String bearerToken = "Bearer " + jwt.getTokenValue();

        // hämtar kundens e-postadress från JWT
        String customerName = jwt.getSubject();

        // skapar ordern och en tillhörande Stripe Checkout-session
        OrderCheckoutResponse response = orderService.createCheckoutOrder(
                orderRequest,
                bearerToken,
                customerName);

        return ResponseEntity.ok(response);
    }

    /**
     * Hämtar den inloggade kundens ordrar, med den senaste först.
     *
     * @param jwt den validerade JWT-token för den inloggade kunden
     * @return kundens ordrar, tom lista om kunden inte har några
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(
            @AuthenticationPrincipal Jwt jwt) {

        // hämtar kundens mejladress från den validerade JWT-token
        String customerName = jwt.getSubject();

        // hämtar endast den inloggade kundens ordrar
        List<OrderResponse> orders =
                orderService.getCustomerOrders(customerName);

        // returnerar ordrarna med HTTP 200 OK
        return ResponseEntity.ok(orders);
    }


}
