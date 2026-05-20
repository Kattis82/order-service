package se.iths.kattis.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;


// DTO som representerar det som skickas in när en order skapas
// record används eftersom den bara behöver bära data, inte ha logik
// @Valid på listan gör att valideringen även körs på varje CreateOrderItemRequest i listan
public record CreateOrderRequest(

        @NotNull
        @Size(min = 1, message = "Ordern måste innehålla minst en produkt")
        List<@Valid CreateOrderItemRequest> items) {
}
