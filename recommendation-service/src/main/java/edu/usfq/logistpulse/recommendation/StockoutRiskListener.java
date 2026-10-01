package edu.usfq.logistpulse.recommendation;

import edu.usfq.logistpulse.contract.StockoutRiskDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

@Component
public class StockoutRiskListener {
    private static final Logger log = LoggerFactory.getLogger(StockoutRiskListener.class);

    static final String FAIL_SKU = "FAIL-TEST";

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handle(StockoutRiskDetected event) {
        // getRetryCount() es 0 en el primer intento (se incrementa al registrar cada fallo).
        var ctx = RetrySynchronizationManager.getContext();
        int attempt = (ctx == null ? 0 : ctx.getRetryCount()) + 1;
        log.info("INTENTO_PROCESAMIENTO attempt={}/{} eventId={} sku={}",
                attempt, RabbitConfig.MAX_ATTEMPTS, event.eventId(), event.sku());

        if (FAIL_SKU.equals(event.sku())) {
            log.warn("INTENTO_FALLIDO attempt={}/{} eventId={} sku={}",
                    attempt, RabbitConfig.MAX_ATTEMPTS, event.eventId(), event.sku());
            throw new IllegalStateException("Fallo controlado: el SKU " + FAIL_SKU
                    + " siempre falla (eventId=" + event.eventId() + ", intento " + attempt + ")");
        }

        // En el siguiente paso este bloque puede persistir una recomendación.
        log.info("RECOMENDACION_PENDIENTE eventId={} sku={} location={} available={} minimum={} action=Revisar compra o transferencia",
                event.eventId(), event.sku(), event.location(), event.available(), event.minimum());
    }

}
