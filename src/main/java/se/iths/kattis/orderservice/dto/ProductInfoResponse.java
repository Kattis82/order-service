package se.iths.kattis.orderservice.dto;

import java.math.BigDecimal;

// DTO som order-service tar emot som svar från product-service
// innehåller en snapshot av produktinformationen vid köptillfället
// likadan record behöver finnas i product-service med identiska fält
public record ProductInfoResponse(Long id, String name, BigDecimal price, int quantity) {
}
