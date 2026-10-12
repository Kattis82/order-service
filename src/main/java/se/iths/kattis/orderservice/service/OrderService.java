package se.iths.kattis.orderservice.service;

import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.iths.kattis.orderservice.client.ProductClient;
import se.iths.kattis.orderservice.dto.*;
import se.iths.kattis.orderservice.mapper.OrderMapper;
import se.iths.kattis.orderservice.model.Order;
import se.iths.kattis.orderservice.model.OrderItem;
import se.iths.kattis.orderservice.model.PaymentStatus;
import se.iths.kattis.orderservice.publisher.OrderPublisher;
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
    // publisher som skickar orderbekräftelse till RabbitMQ-kön efter att ordern sparats
    // email-service tar emot meddelandet och skickar bekräftelsemail till kunden
    private final OrderPublisher orderPublisher;
    // skapar Stripe Checkout-sessioner för ordrar och returnerar betalningslänken
    private final StripeCheckoutService stripeCheckoutService;


    // metod som skapar en ny order
    // orderRequest innehåller produktlistan man får in
    // bearerToken skickas vidare till product-service för JWT-validering
    // customerName är kundens mejladress hämtad från JWT
    public OrderResponse createOrder(
            CreateOrderRequest orderRequest,
            String bearerToken,
            String customerName) {


        // skickar produktlistan till ProductService för lageruppdatering
        // om en produkt saknas eller lagersaldot inte räcker kastas ett exception
        // från ProductClient och ordern sparas inte
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

        // konverterar till OrderResponse och återanvänder den både för
        // RabbitMQ-meddelandet och svaret tillbaka
        OrderResponse orderResponse = orderMapper.toOrderResponse(savedOrder);

        // skickar orderbekräftelsen till kön (RabbitMQ för email-service)
        orderPublisher.publishOrderConfirmation(orderResponse);

        // returnerar samma objekt
        return orderResponse;
    }

    // ny metod (betalningslösning)
    // metoden Hämtar produktuppgifter och pris från Product Service
    // kontrollerar lagersaldot innan checkout skapas
    // sparar ordern som PENDING
    // skapar en Stripe-session kopplad till orderns ID
    // sparar Stripe-sessionens ID i databasen
    public OrderCheckoutResponse createCheckoutOrder(
            CreateOrderRequest orderRequest,
            String bearerToken,
            String customerName) throws StripeException {

        List<OrderItem> items = orderRequest.items().stream()
                .map(requestItem -> {
                    ProductDetailsResponse product =
                            productClient.getProductById(
                                    requestItem.productId(),
                                    bearerToken);

                    if (product.stock() < requestItem.quantity()) {
                        throw new IllegalArgumentException(
                                "Otillräckligt lagersaldo för: "
                                        + product.name());
                    }

                    OrderItem item = new OrderItem();
                    item.setName(product.name());
                    item.setPrice(product.price());
                    item.setQuantity(requestItem.quantity());
                    return item;
                })
                .toList();

        Order order = new Order();
        order.setCustomerName(customerName);
        order.setOrderDate(LocalDateTime.now());
        order.setPaymentStatus(PaymentStatus.PENDING);

        items.forEach(item -> item.setOrder(order));
        order.setItems(items);

        BigDecimal totalPrice = items.stream()
                .map(item -> item.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalPrice(totalPrice);

        Order savedOrder = orderRepository.save(order);

        OrderCheckoutResponse checkoutResponse =
                stripeCheckoutService.createCheckoutSession(savedOrder);

        savedOrder.setStripeSessionId(checkoutResponse.sessionId());
        orderRepository.save(savedOrder);

        return checkoutResponse;
    }

    // ny metod (betalningslösning)
    // metoden skyddar mot: en order som inte finns kan inte markeras som betald
    // en Stripe-session som inte hör till ordern avvisas
    // en redan betald order ändras inte igen
    // uppdaterar ordern när Stripe har bekräftat betalningen
    public void markOrderPaid(Long orderId, String stripeSessionId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Ordern hittades inte: " + orderId));

        // kontrollera att Stripe-sessionen tillhör just denna order
        if (!stripeSessionId.equals(order.getStripeSessionId())) {
            throw new IllegalArgumentException(
                    "Stripe-sessionen matchar inte ordern");
        }

        // ändra endast status om ordern fortfarande väntar på betalning
        if (order.getPaymentStatus() == PaymentStatus.PENDING) {
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
        }
    }

}
