package edu.usfq.logistpulse.audit;

import edu.usfq.logistpulse.contract.StockoutRiskDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockoutRiskAuditListener {
    private static final Logger log = LoggerFactory.getLogger(StockoutRiskAuditListener.class);

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handle(StockoutRiskDetected event) {
        log.info("AUDITORIA_REGISTRADA eventId={} sku={} location={} available={} minimum={} occurredAt={}",
                event.eventId(), event.sku(), event.location(), event.available(), event.minimum(), event.occurredAt());
    }
}
