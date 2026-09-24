package com.rikkeibank.transaction.event;

import com.rikkeibank.common.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    public static final String TOPIC = "transaction-events";

    public void publishEvent(TransactionEvent event) {
        log.info("Publishing Kafka event {} for transaction: {}", event.getEventType(), event.getTransactionReference());
        try {
            CompletableFuture<?> future = kafkaTemplate.send(TOPIC, event.getTransactionReference(), event);
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("--- [KAFKA EVENT PRODUCED] Successfully sent event {} to topic {} ---",
                            event.getEventType(), TOPIC);
                } else {
                    log.warn("--- [KAFKA WARNING] Kafka broker unavailable or failed to ack: {}. Event was logged locally. ---",
                            ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.warn("--- [KAFKA WARNING] Error sending event: {}. Transaction flow continues. ---", ex.getMessage());
        }
    }
}
