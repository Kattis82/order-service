package se.iths.kattis.orderservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// sparar snapshot = orderItem ska vara fristående historik
// OrderItem sparar produktens data vid köptillfället

@Entity
@Getter
@Setter
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;  // kopierad från product-service
    private BigDecimal price;  // vid köp (snapshot, för att orderhistoriken ska bli rätt)
    private int quantity;
}
