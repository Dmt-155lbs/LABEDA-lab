package edu.usfq.logistpulse.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inventory")
public class RecommendationProxyController {

    private final RestClient recommendationClient;

    public RecommendationProxyController(
            @Value("${recommendation.base-url:http://recommendation-service:8081}")
            String baseUrl) {
        this.recommendationClient =
                RestClient.builder().baseUrl(baseUrl).build();
    }

    @GetMapping("/recommendation-preview")
    public Object preview(
            @RequestParam String sku,
            @RequestParam int available,
            @RequestParam int minimum) {
        try {
            return recommendationClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/recommendations/preview")
                            .queryParam("sku", sku)
                            .queryParam("available", available)
                            .queryParam("minimum", minimum)
                            .build())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "recommendation-service no está disponible");
        }
    }
}
