package se.iths.kattis.orderservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.iths.kattis.orderservice.dto.CreateOrderRequest;
import se.iths.kattis.orderservice.dto.OrderResponse;
import se.iths.kattis.orderservice.service.OrderService;


// controller som tar emot anrop för att skapa orders
// @RequestMapping("/orders") = alla endpoints börjar med /orders
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // tar emot en POST-request för att skapa en ny order
    // @Valid aktiverar valideringen på CreateOrderRequest
    // @AuthenticationPrincipal Jwt jwt = ger tillgång till JWT-token
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
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
}
