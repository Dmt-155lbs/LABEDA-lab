package edu.usfq.logistpulse.recommendation;

import edu.usfq.logistpulse.contract.StockoutRiskDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StockoutRiskListener {
    private static final Logger log = LoggerFactory.getLogger(StockoutRiskListener.class);

    static final String FAIL_SKU = "FAIL-TEST";

    // Idempotencia (Caso 4): eventIds ya procesados con exito. Solo en memoria: se pierde al reiniciar
    // el servicio (limitacion deliberada). Set thread-safe porque el container puede usar varios hilos.
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handle(StockoutRiskDetected event) {
        // getRetryCount() es 0 en el primer intento (se incrementa al registrar cada fallo).
        var ctx = RetrySynchronizationManager.getContext();
        int attempt = (ctx == null ? 0 : ctx.getRetryCount()) + 1;
        log.info("INTENTO_PROCESAMIENTO attempt={}/{} eventId={} sku={}",
                attempt, RabbitConfig.MAX_ATTEMPTS, event.eventId(), event.sku());

        // Duplicado: ya se proceso con exito antes. Se retorna normalmente (sin excepcion) para que el
        // mensaje se confirme (ack) y no se reintente ni termine en la DLQ.
        if (processedEventIds.contains(event.eventId())) {
            log.info("EVENTO_DUPLICADO_IGNORADO eventId={} sku={}", event.eventId(), event.sku());
            return;
        }

        if (FAIL_SKU.equals(event.sku())) {
            log.warn("INTENTO_FALLIDO attempt={}/{} eventId={} sku={}",
                    attempt, RabbitConfig.MAX_ATTEMPTS, event.eventId(), event.sku());
            throw new IllegalStateException("Fallo controlado: el SKU " + FAIL_SKU
                    + " siempre falla (eventId=" + event.eventId() + ", intento " + attempt + ")");
        }

        // En el siguiente paso este bloque puede persistir una recomendación.
        log.info("RECOMENDACION_PENDIENTE eventId={} sku={} location={} available={} minimum={} action=Revisar compra o transferencia",
                event.eventId(), event.sku(), event.location(), event.available(), event.minimum());

        // IMPORTANTE: el eventId se registra SOLO DESPUES de que la logica de negocio termino con exito.
        // Si se marcara antes, el 2.o intento de un evento que falla (p. ej. FAIL-TEST) se veria como
        // duplicado, se ignoraria y se confirmaria: nunca llegaria a la DLQ y se romperia el Caso 3.
        // Si la logica lanza excepcion, este punto no se alcanza y el eventId queda sin registrar,
        // de modo que los reintentos pueden volver a procesarlo.
        processedEventIds.add(event.eventId());
    }

}
