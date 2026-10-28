package se.iths.kattis.orderservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import se.iths.kattis.orderservice.dto.ProductDetailsResponse;
import se.iths.kattis.orderservice.dto.ProductInfoResponse;
import se.iths.kattis.orderservice.dto.ProductStockRequest;

import java.util.List;


/**
 * Klient för REST-anrop till Product Service. Det enda stället i Order
 * Service som pratar med Product Service.
 */
// @Component = Spring hanterar klassen som en bean
@Component
@RequiredArgsConstructor
public class ProductClient {

    // RestClient, konfigurerad med product-service bas-url i ClientConfig
    private final RestClient productRestClient;

    // intern hemlighet för anrop till Product Service
    @Value("${internal.service-token}")
    private String internalServiceToken;


    /**
     * Minskar lagersaldot för en lista med produkter
     *
     * @param items       produkt-id och antal
     * @param bearerToken kundens JWT, skickas som Authorization-header
     * @return en lista med produktuppgifter vid köptillfället
     * @throws org.springframework.web.server.ResponseStatusException med Product Services
     *                                                                statuskod, till exempel 404 om produkten saknas eller 400 om lagret inte räcker
     */
    public List<ProductInfoResponse> decreaseStock(List<ProductStockRequest> items,
                                                   String bearerToken) {

        try {
            // ParameterizedTypeReference behövs när man förväntar sig en lista som svar
            // annars vet inte Jackson vilken typ objekten i listan ska deserialiseras till
            return productRestClient.post()
                    .uri("/products/stock/decrease")
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


    /**
     * Återställer lagersaldot. Anropet autentiseras med den interna
     * hemligheten i headern {@code X-Internal-Service-Token} i stället för kundens JWT.
     *
     * @param items produkt-id och antal som ska läggas tillbaka
     * @throws org.springframework.web.server.ResponseStatusException om Product Service svarar med fel
     */
    public void restoreStock(List<ProductStockRequest> items) {
        try {
            // skickar ett POST-anrop till Product Services endpoint
            // endpointen lägger tillbaka antalet produkter på lagret
            productRestClient.post()
                    .uri("/products/stock/restore")
                    // skicka den interna hemligheten för autentisering
                    .header("X-Internal-Service-Token", internalServiceToken)
                    // skicka produkt-ID och antal som ska återställas
                    .body(items)
                    // skickar anropet och kontrollerar att svaret inte är ett fel
                    // inget innehåll behövs tillbaka från Product Service
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException exception) {
            // om Product Service svarar med ett fel, skickas det vidare
            // HTTP-statusen och felmeddelandet till den som anropade Order Service
            throw new ResponseStatusException(
                    exception.getStatusCode(),
                    "Kunde inte återställa lagersaldot: "
                            + exception.getResponseBodyAsString());
        }
    }

    /**
     * Hämtar information om en produkt utan att ändra lagersaldot.
     *
     * @param productId   produktens id
     * @param bearerToken kundens JWT, skickas som Authorization-header
     * @return produktens uppgifter
     * @throws org.springframework.web.server.ResponseStatusException om Product Service svarar med fel
     */
    public ProductDetailsResponse getProductById(
            Long productId,
            String bearerToken) {

        try {
            return productRestClient.get()
                    .uri("/products/{id}", productId)
                    .header("Authorization", bearerToken)
                    .retrieve()
                    .body(ProductDetailsResponse.class);

        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    exception.getStatusCode(),
                    "Fel från product-service: "
                            + exception.getResponseBodyAsString());
        }
    }

}
