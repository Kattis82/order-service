package se.iths.kattis.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.iths.kattis.orderservice.client.ProductClient;
import se.iths.kattis.orderservice.dto.CreateOrderRequest;
import se.iths.kattis.orderservice.dto.OrderResponse;
import se.iths.kattis.orderservice.dto.ProductInfoResponse;
import se.iths.kattis.orderservice.mapper.OrderMapper;
import se.iths.kattis.orderservice.model.Order;
import se.iths.kattis.orderservice.model.OrderItem;
import se.iths.kattis.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    // mapper som konverterar mellan entiteter och DTO:er
    private final OrderMapper orderMapper;
    // klient som anropar product-service via REST
    private final ProductClient productClient;


    // metod som skapar en ny order
    public OrderResponse createOrder(
            CreateOrderRequest orderRequest,  // tar emot request från controllern,
            String bearerToken, // tar emot bearer-token från JWT och
            String customerName) {  // tar emot kundens mailadress

        // skickar produktlistan till ProductClient
        // CreateOrderRequest innehåller List<ProductStockRequest>
        // om något går fel (produkt saknas, stock räcker inte) kastas ett exception
        // i ProductClient som stoppar hela flödet, ingen order sparas då
        List<ProductInfoResponse> products = productClient.decreaseStock(orderRequest.items(),
                bearerToken);


        // skapar ett nytt Order-objekt och fyller i grunduppgifterna
        // customerName hämtas från JWT (kundens mejladress)
        // orderDate sätts till det klockslag ordern skapas
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setOrderDate(LocalDateTime.now());

        // konverterar varje ProductInfoResponse till en OrderItem (snapshot)
        // sätter order-referensen manuellt eftersom mappern ignorerar det fältet
        List<OrderItem> items = products.stream()
                .map(product -> {
                    OrderItem item = orderMapper.toOrderItem(product);
                    // kopplar orderraden till ordern så JPA vet vilken order den tillhör
                    item.setOrder(order);
                    return item;
                })
                .toList();

        order.setItems(items);

        // Räknar ut totalpriset
        // multiplicerar pris med antal för varje rad och summerar ihop allt
        BigDecimal totalPrice = items.stream()
                .map(item -> item.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()))) // multiply = gånger (*)
                .reduce(BigDecimal.ZERO, // BigDecimal.ZERO = startvärde 0
                        BigDecimal::add); // add = plus (+)

        order.setTotalPrice(totalPrice);


        // sparar ordern i databasen
        // eftersom cascade = ALL sparas även alla OrderItems automatiskt
        Order savedOrder = orderRepository.save(order);

        // läggas till här sen = skicka meddelande till RabbitMQ för email-service
        // ska göras här efter att ordern sparats i databasen


        // konverterar den sparade Order-entiteten till OrderResponse och returnerar till controllern
        return orderMapper.toOrderResponse(savedOrder);
    }

}
