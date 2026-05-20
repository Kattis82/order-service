package se.iths.kattis.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import se.iths.kattis.orderservice.dto.OrderResponse;
import se.iths.kattis.orderservice.dto.ProductInfoResponse;
import se.iths.kattis.orderservice.model.Order;
import se.iths.kattis.orderservice.model.OrderItem;

// MapStruct-mapper genererar automatiskt kod för att konvertera mellan objekt
// componentModel = "spring" gör att Spring hanterar mappern som en vanlig bean
// så att den kan injiceras med @RequiredArgsConstructor eller @Autowired
@Mapper(componentModel = "spring")
public interface OrderMapper {

    // konverterar ProductInfo (svar från product-service) till OrderItem (entitet)
    // ignore = true på "order" eftersom det fältet inte finns i ProductInfo
    // och sätts manuellt i OrderService istället
    @Mapping(target = "order", ignore = true)
    OrderItem toOrderItem(ProductInfoResponse productInfoResponse);

    // konverterar Order (entitet) till OrderResponse (DTO ut)
    // MapStruct hittar automatiskt matchande fält eftersom de heter likadant
    OrderResponse toOrderResponse(Order order);
}
