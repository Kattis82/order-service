package se.iths.kattis.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// entitetsklass, representerar en order i databasen

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime orderDate; // när ordern skapades

    @Column(nullable = false)
    private String customerName; // hämtar username från JWT (kundens email)

    // en order kan ha många orderrader (OrderItem)
    // mappedBy = "order" betyder att OrderItem äger relationen via sitt "order"-fält
    // cascade = ALL betyder att när en Order sparas/tas bort påverkas även dess OrderItems
    // orphanRemoval = true betyder att OrderItems tas bort om de tas bort från listan
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // totalpriset för hela ordern, räknas ut i OrderService
    @Column(nullable = false)
    private BigDecimal totalPrice;
}
