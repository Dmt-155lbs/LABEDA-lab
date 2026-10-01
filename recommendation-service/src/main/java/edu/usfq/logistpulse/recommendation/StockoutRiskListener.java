package edu.usfq.logistpulse.recommendation;

import edu.usfq.logistpulse.contract.StockoutRiskDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockoutRiskListener {
    private static final Logger log = LoggerFactory.getLogger(StockoutRiskListener.class);

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handle(StockoutRiskDetected event) {
        // En el siguiente paso este bloque puede persistir una recomendación.
        log.info("RECOMENDACION_PENDIENTE eventId={} sku={} location={} available={} minimum={} action=Revisar compra o transferencia",
                event.eventId(), event.sku(), event.location(), event.available(), event.minimum());
    }

}
