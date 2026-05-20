package se.iths.kattis.orderservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import se.iths.kattis.orderservice.dto.ProductInfoResponse;
import se.iths.kattis.orderservice.dto.ProductStockRequest;

import java.util.List;

// klientklass, ansvarar för REST-anrop till product-service
// ligger i order-service och är det enda stället som pratar med product-service
// @Component = Spring hanterar klassen som en bean
@Component
@RequiredArgsConstructor
public class ProductClient {

    // RestClient, konfigurerad med product-service bas-url i ClientConfig
    private final RestClient productRestClient;

    // skickar en POST-request till product-service för att minska lagersaldot
    // tar emot en lista med ProductStockRequest (productId + quantity)
    // bearerToken skickas med som Authorization-header så product-service kan validera JWT
    // returnerar en lista med ProductInfoResponse = en snapshot av varje produkt vid köptillfället
    public List<ProductInfoResponse> decreaseStock(List<ProductStockRequest> items,
                                                   String bearerToken) {

        try {
            // ParameterizedTypeReference behövs när man förväntar sig en lista som svar
            // annars vet inte Jackson vilken typ objekten i listan ska deserialiseras till
            return productRestClient.post()
                    .uri("products/stock/decrease")
                    // JWT skickas vidare så product-service vet att anropet är auktoriserat
                    .header("Authorization", bearerToken)
                    .body(items)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

        } catch (RestClientResponseException exception) {
            // RestClientResponseException kastas när product-service svarar med felstatus
            // 404 om en produkt inte finns, 400 om stock inte räcker
            // felet fångas och kastar ett eget ResponseStatusException
            // så att order-service ger ett tydligt HTTP-svar tillbaka
            throw new ResponseStatusException(
                    exception.getStatusCode(), "Fel från product-service: "
                    + exception.getResponseBodyAsString());
        }
        
    }
}
