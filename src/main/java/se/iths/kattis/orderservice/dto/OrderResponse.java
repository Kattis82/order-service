package se.iths.kattis.orderservice.dto;

import se.iths.kattis.orderservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Svar för en order: kund, datum, orderrader, totalpris och betalningsstatus.
 */
// DTO som order-service skickar tillbaka när en order skapats
// returnerar aldrig entitetsklassen direkt
public record OrderResponse(
        Long id,
        String customerName,
        LocalDateTime orderDate,
        // items är de sparade orderraderna med produktinfo
        List<ProductInfoResponse> items,
        BigDecimal totalPrice,
        // betalningsstatus från ordern i databasen
        PaymentStatus paymentStatus

) {
}
