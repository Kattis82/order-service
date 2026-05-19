package se.iths.kattis.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.iths.kattis.orderservice.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
