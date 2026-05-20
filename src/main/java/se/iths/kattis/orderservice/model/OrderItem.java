package se.iths.kattis.orderservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// entitetsklass, representerar en orderrad i databasen
// sparar en snapshot av produkten vid köptillfället
// om produktens pris ändras senare påverkas inte gamla orders

@Entity
@Getter
@Setter
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Snapshot av produktnamnet vid köptillfället
    @Column(nullable = false)
    private String name;  // kopierad från product-service

    // Snapshot av priset vid köptillfället
    @Column(nullable = false)
    private BigDecimal price;  // (snapshot, för att orderhistoriken ska bli rätt)

    // antal av produkten som beställdes
    @Column(nullable = false)
    private int quantity;

    // kopplar orderraden till en specifik order i databasen
    // @ManyToOne eftersom många orderrader kan tillhöra samma order
    // @JoinColumn skapar en kolumn "order_id" i order_items-tabellen
    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
}
