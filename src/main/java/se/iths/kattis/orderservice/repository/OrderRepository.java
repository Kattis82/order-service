package se.iths.kattis.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.iths.kattis.orderservice.model.Order;
import se.iths.kattis.orderservice.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {


    // hämtar ordrar för en viss kund och sorterar med den senaste ordern först
    List<Order> findByCustomerNameOrderByOrderDateDesc(String customerName);

    // hämtar kundens senaste order med en viss betalningsstatus
    Optional<Order> findFirstByCustomerNameAndPaymentStatusOrderByOrderDateDesc(
            String customerName,
            PaymentStatus paymentStatus);

    List<Order> findByPaymentStatusAndOrderDateBefore(
            PaymentStatus paymentStatus, LocalDateTime cutoff);

}
