package edu.usfq.logistpulse.recommendation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationApiController {

    @GetMapping("/preview")
    public Preview preview(
            @RequestParam String sku,
            @RequestParam int available,
            @RequestParam int minimum) {

        boolean risk = available <= minimum;
        String action = risk
                ? "Revisar compra o transferencia"
                : "No se requiere acción";

        return new Preview(sku, available, minimum, risk, action);
    }

    public record Preview(
            String sku,
            int available,
            int minimum,
            boolean riskDetected,
            String action) {}
}
