package se.iths.kattis.orderservice.dto;

import java.math.BigDecimal;

/**
 * Produktens detaljer, bland annat lagersaldo, hämtade från Product Service.
 */
// motsvarar de fyra fält vi behöver från ProductResponseDto i Product Service
// hämtar bara den information som behövs för betalningsflödet
public record ProductDetailsResponse(
        Long id,
        String name,
        BigDecimal price,
        int stock
) {
}
