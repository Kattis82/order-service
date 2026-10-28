package se.iths.kattis.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.iths.kattis.orderservice.model.Order;
import se.iths.kattis.orderservice.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Databasåtkomst för ordrar.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {


    /**
     * Hämtar en kunds ordrar, med den senaste först.
     *
     * @param customerName kundens e-postadress
     * @return kundens ordrar
     */
    List<Order> findByCustomerNameOrderByOrderDateDesc(String customerName);

    /**
     * Hämtar kundens senaste order med angiven betalningsstatus.
     *
     * @param customerName  kundens e-postadress
     * @param paymentStatus betalningsstatus, till exempel PENDING
     * @return den senaste ordern, om det finns någon
     */
    Optional<Order> findFirstByCustomerNameAndPaymentStatusOrderByOrderDateDesc(
            String customerName,
            PaymentStatus paymentStatus);

    /**
     * Hämtar ordrar med viss betalningsstatus som skapats före en tidpunkt.
     *
     * @param paymentStatus betalningsstatus, till exempel PENDING
     * @param cutoff        ordrar skapade före denna tidpunkt
     * @return matchande ordrar
     */
    List<Order> findByPaymentStatusAndOrderDateBefore(
            PaymentStatus paymentStatus, LocalDateTime cutoff);

}
