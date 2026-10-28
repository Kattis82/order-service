package se.iths.kattis.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import se.iths.kattis.orderservice.dto.OrderResponse;
import se.iths.kattis.orderservice.dto.ProductInfoResponse;
import se.iths.kattis.orderservice.model.Order;
import se.iths.kattis.orderservice.model.OrderItem;


/**
 * MapStruct-mapper som konverterar mellan entiteter och DTO:er för ordrar.
 * Implementationen genereras vid bygget och hanteras av Spring som en bean.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {


    /**
     * Konverterar produktuppgifter från Product Service till en orderrad.
     * Fälten {@code id} och {@code order} ignoreras, {@code order} sätts i OrderService.
     *
     * @param productInfoResponse produktens uppgifter vid köptillfället
     * @return en orderrad som ännu inte är kopplad till någon order
     */
    // ignore = true på "order" eftersom det fältet inte finns i ProductInfo
    // och sätts manuellt i OrderService istället
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    OrderItem toOrderItem(ProductInfoResponse productInfoResponse);

    /**
     * Konverterar en orderrad till produktuppgifter, som används när en OrderResponse byggs.
     *
     * @param orderItem orderraden
     * @return orderradens produktuppgifter
     */
    ProductInfoResponse toProductInfoResponse(OrderItem orderItem);

    /**
     * Konverterar en order till det svar som skickas till frontend och till RabbitMQ.
     *
     * @param order ordern som ska konverteras
     * @return ordern som OrderResponse
     */
    // MapStruct hittar automatiskt matchande fält eftersom de heter likadant
    OrderResponse toOrderResponse(Order order);
}
