package edu.usfq.logistpulse.inventory;

import java.time.Instant;
import java.util.UUID;

import edu.usfq.logistpulse.contract.StockoutRiskDetected;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inventory")
public class StockController {
    private static final String ROUTING_KEY = "inventory.stockout-risk.detected";
    private final RabbitTemplate rabbitTemplate;

    public StockController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/stock")
    public StockResponse registerStock(@RequestBody StockRequest request) {
        if (request.sku() == null || request.sku().isBlank()
                || request.location() == null || request.location().isBlank()
                || request.available() < 0 || request.minimum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sku, location y valores no negativos son obligatorios");
        }

        boolean risk = request.available() <= request.minimum();
        String eventId = null;
        if (risk) {
            StockoutRiskDetected event = new StockoutRiskDetected(
                    UUID.randomUUID().toString(), Instant.now().toString(),
                    request.sku(), request.location(), request.available(), request.minimum());
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, ROUTING_KEY, event);
            eventId = event.eventId();
        }
        return new StockResponse(request.sku(), request.location(), request.available(),
                request.minimum(), risk, eventId,
                risk ? "Riesgo detectado; evento publicado" : "Stock dentro del nivel esperado");
    }

    public record StockRequest(String sku, String location, int available, int minimum) {}
    public record StockResponse(String sku, String location, int available, int minimum,
                                boolean riskDetected, String eventId, String message) {}
}
