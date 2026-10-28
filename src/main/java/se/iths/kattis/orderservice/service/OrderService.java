package se.iths.kattis.orderservice.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Optional;

/**
 * Affärslogik för ordrar: skapa ordrar, starta betalning via Stripe,
 * uppdatera orderstatus och hämta kundens orderhistorik.
 */
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


    /**
     * Skapar en order direkt, utan betalning. Lagret minskas i Product Service,
     * ordern sparas och orderbekräftelsen publiceras till RabbitMQ.
     *
     * @param orderRequest produkter och antal
     * @param bearerToken  kundens JWT, skickas vidare till Product Service
     * @param customerName kundens e-postadress från JWT
     * @return den sparade ordern
     * @throws org.springframework.web.server.ResponseStatusException om Product Service
     *                                                                nekar, till exempel när en produkt saknas eller lagret inte räcker
     */
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


    /**
     * Skapar en order för Stripe-betalning. Priser hämtas från Product Service,
     * lagret minskas och ordern sparas med status PENDING. Har kunden redan en
     * öppen Checkout-session återanvänds den i stället för att lagret minskas igen.
     * Om ordern eller Stripe-sessionen inte kan skapas återställs lagret.
     *
     * @param orderRequest produkter och antal
     * @param bearerToken  kundens JWT, skickas vidare till Product Service
     * @param customerName kundens e-postadress från JWT
     * @return order-id, Stripe-session-id och betalningslänk
     * @throws StripeException          vid fel i anrop till Stripe
     * @throws IllegalArgumentException om lagersaldot inte räcker för en produkt
     * @throws IllegalStateException    om kundens tidigare checkout redan är slutförd
     * @throws RuntimeException         om ordern eller Stripe-sessionen inte kunde skapas
     */
    public OrderCheckoutResponse createCheckoutOrder(
            CreateOrderRequest orderRequest,
            String bearerToken,
            String customerName) throws StripeException {

        // kontrollera om kunden redan har en obetald order
        Optional<Order> existingOrder =
                orderRepository
                        .findFirstByCustomerNameAndPaymentStatusOrderByOrderDateDesc(
                                customerName,
                                PaymentStatus.PENDING);

        if (existingOrder.isPresent()
                && existingOrder.get().getStripeSessionId() != null) {

            Order order = existingOrder.get();

            // hämta den befintliga betalningssessionen från Stripe
            Session session = stripeCheckoutService.getCheckoutSession(
                    order.getStripeSessionId());

            // återanvänd en öppen session i stället för att minska lagret igen
            if ("open".equals(session.getStatus())) {
                return new OrderCheckoutResponse(
                        order.getId(),
                        session.getId(),
                        session.getUrl());
            }

            // återställ lagret om sessionen har löpt ut
            if ("expired".equals(session.getStatus())) {
                expirePendingOrder(order.getId(), session.getId());
            }

            // skapa inte ytterligare en order om checkout redan har slutförts
            if ("complete".equals(session.getStatus())) {
                throw new IllegalStateException(
                        "Betalningen har slutförts eller behandlas redan.");
            }
        }

        // hämta produktuppgifter och kontrollera att lagret verkar räcka
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

                    // skapa en orderrad med produktens uppgifter
                    OrderItem item = new OrderItem();

                    // spara produktens ID så att lagret kan återställas senare
                    item.setProductId(requestItem.productId());

                    // spara namn och pris vid köptillfället
                    item.setName(product.name());
                    item.setPrice(product.price());
                    item.setQuantity(requestItem.quantity());

                    return item;
                })
                .toList();

        // samla produkt-ID och antal för alla orderrader
        List<ProductStockRequest> stockRequests = orderRequest.items().stream()
                .map(stockItem -> new ProductStockRequest(
                        stockItem.productId(),
                        stockItem.quantity()))
                .toList();

        // minska lagret i ett enda anrop
        // Product Service gör den slutliga kontrollen av lagersaldot
        productClient.decreaseStock(stockRequests, bearerToken);

        // behövs för att kunna ta bort ordern om något går fel senare
        Order savedOrder = null;

        try {
            // skapa ordern och markera att betalningen väntar
            Order order = new Order();
            order.setCustomerName(customerName);
            order.setOrderDate(LocalDateTime.now());
            order.setPaymentStatus(PaymentStatus.PENDING);

            // koppla orderraderna till ordern
            items.forEach(item -> item.setOrder(order));
            order.setItems(items);

            // beräkna totalsumman utifrån de sparade produktpriserna
            BigDecimal totalPrice = items.stream()
                    .map(item -> item.getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            order.setTotalPrice(totalPrice);

            // spara ordern innan Stripe-sessionen skapas
            savedOrder = orderRepository.save(order);

            // skapa Stripe-sessionen för ordern
            OrderCheckoutResponse checkoutResponse =
                    stripeCheckoutService.createCheckoutSession(savedOrder);

            // spara Stripe-sessionens ID på ordern
            savedOrder.setStripeSessionId(checkoutResponse.sessionId());
            orderRepository.save(savedOrder);

            return checkoutResponse;

        } catch (Exception exception) {
            // försök återställa lagret om ordern eller Stripe-sessionen misslyckas
            try {
                // återställ lagret med den interna hemligheten i stället för kundens JWT
                productClient.restoreStock(stockRequests);
            } catch (Exception restoreException) {
                // spara återställningsfelet tillsammans med det ursprungliga felet
                exception.addSuppressed(restoreException);
            }

            // ta bort ordern om den hann sparas
            if (savedOrder != null) {
                orderRepository.delete(savedOrder);
            }

            // skicka vidare felet så att anropet inte ser ut att ha lyckats
            throw new RuntimeException(
                    "Kunde inte skapa order eller Stripe-session.",
                    exception);
        }
    }


    /**
     * Markerar en order som betald när Stripe har bekräftat betalningen och
     * skickar orderbekräftelse via RabbitMQ. Ordern ändras bara om den är PENDING,
     * så samma händelse kan tas emot flera gånger utan dubbel bekräftelse.
     *
     * @param orderId         orderns id
     * @param stripeSessionId Stripe-sessionens id, måste tillhöra ordern
     * @throws IllegalArgumentException om ordern inte finns eller sessionen inte matchar
     */
    // en order som inte finns kan inte markeras som betald
    // en Stripe-session som inte hör till ordern avvisas
    // en redan betald order ändras inte igen och får ingen ny orderbekräftelse
    // uppdaterar ordern när Stripe har bekräftat betalningen
    // och skickar orderbekräftelsen till Email Service
    @Transactional
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

        // behandla bara ordern första gången den blir betald
        if (order.getPaymentStatus() == PaymentStatus.PENDING) {
            order.setPaymentStatus(PaymentStatus.PAID);

            // spara den betalda ordern i databasen
            Order savedOrder = orderRepository.save(order);

            // skapa svaret som e-posttjänsten redan förstår
            OrderResponse orderResponse = orderMapper.toOrderResponse(savedOrder);

            // skicka orderbekräftelsen via RabbitMQ till Email Service
            orderPublisher.publishOrderConfirmation(orderResponse);
        }
    }


    /**
     * Hanterar en utgången Stripe Checkout-session. Lagret återställs i Product
     * Service och ordern markeras som EXPIRED, men bara om ordern fortfarande är
     * PENDING. Misslyckas återställningen sparas inte ordern som EXPIRED.
     *
     * @param orderId         orderns id
     * @param stripeSessionId Stripe-sessionens id, måste tillhöra ordern
     * @throws IllegalArgumentException                               om ordern inte finns eller sessionen inte matchar
     * @throws org.springframework.web.server.ResponseStatusException om lagret inte kan återställas
     */
    @Transactional
    public void expirePendingOrder(Long orderId, String stripeSessionId) {

        // hämta ordern, om den saknas kan lagret inte återställas
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Ordern hittades inte: " + orderId));

        // kontrollera att Stripe-sessionen hör till just denna order
        if (!stripeSessionId.equals(order.getStripeSessionId())) {
            throw new IllegalArgumentException(
                    "Stripe-sessionen matchar inte ordern");
        }

        // om ordern redan har behandlats ska lagret inte återställas igen
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }

        // samla produkt-ID och antal från orderns sparade orderrader
        List<ProductStockRequest> stockRequests = order.getItems().stream()
                .map(item -> new ProductStockRequest(
                        item.getProductId(),
                        item.getQuantity()))
                .toList();

        // återställ lagret via Product Service med den interna tokenen
        // om anropet misslyckas sparas inte ordern som EXPIRED,
        // så att felet kan hanteras och återställningen försöka igen
        productClient.restoreStock(stockRequests);

        // markera ordern som utgången först när lagret har återställts
        order.setPaymentStatus(PaymentStatus.EXPIRED);
        orderRepository.save(order);
    }


    /**
     * Hämtar en kunds ordrar, med den senaste först.
     *
     * @param customerName kundens e-postadress
     * @return kundens ordrar som OrderResponse
     */
    public List<OrderResponse> getCustomerOrders(String customerName) {

        // hämtar endast ordrar som tillhör kunden
        List<Order> orders = orderRepository
                .findByCustomerNameOrderByOrderDateDesc(customerName);

        // omvandlar varje order till det format som frontend använder
        return orders.stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }


    /**
     * Körs varje minut och rensar ordrar som fortfarande väntar på betalning trots att
     * Checkout-sessionen borde ha gått ut, till exempel när en webhook har missats.
     * Betalda sessioner markeras som betalda, öppna sessioner avslutas hos Stripe och
     * lagret återställs. Fel för en order stoppar inte övriga.
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanUpStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(35);

        List<Order> staleOrders = orderRepository
                .findByPaymentStatusAndOrderDateBefore(
                        PaymentStatus.PENDING, cutoff);

        for (Order order : staleOrders) {
            try {
                // saknas session-ID kan vi inte kontrollera hos Stripe
                if (order.getStripeSessionId() == null) {
                    continue;
                }

                Session session = stripeCheckoutService
                        .getCheckoutSession(order.getStripeSessionId());

                // betalningen gick igenom men webhooken missades
                if ("complete".equals(session.getStatus())
                        && "paid".equals(session.getPaymentStatus())) {
                    markOrderPaid(order.getId(), session.getId());
                    continue;
                }

                // sessionen är fortfarande öppen: avsluta den hos Stripe först
                if ("open".equals(session.getStatus())) {
                    stripeCheckoutService.expireCheckoutSession(session.getId());
                }

                // återställ lagret och markera ordern som EXPIRED
                expirePendingOrder(order.getId(), session.getId());

            } catch (Exception e) {
                // ett fel ska inte stoppa övriga ordrar, nästa körning försöker igen
                System.err.println("Kunde inte rensa order "
                        + order.getId() + ": " + e.getMessage());
            }
        }
    }


}
