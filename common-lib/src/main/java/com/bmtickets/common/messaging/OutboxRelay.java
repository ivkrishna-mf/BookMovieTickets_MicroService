package com.bmtickets.common.messaging;

import com.bmtickets.common.events.EventTypes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polling publisher for the transactional outbox (at-least-once delivery; consumers are idempotent).
 * For very high event volumes the same table can be streamed by Debezium CDC (MSK Connect) instead.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final Duration retention;

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${app.outbox.batch-size:100}") int batchSize,
                       @Value("${app.outbox.retention:P7D}") Duration retention) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:250}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
        for (OutboxEvent e : batch) {
            ProducerRecord<String, String> record = new ProducerRecord<>(e.getTopic(), e.getAggregateId(), e.getPayload());
            record.headers().add(EventTypes.HEADER_EVENT_TYPE, e.getEventType().getBytes(StandardCharsets.UTF_8));
            record.headers().add(EventTypes.HEADER_EVENT_ID, e.getId().toString().getBytes(StandardCharsets.UTF_8));
            try {
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);   // wait for broker ack (acks=all)
                e.markPublished();
            } catch (Exception ex) {
                // Abort the batch; rows stay unpublished and are retried on the next tick.
                log.warn("Outbox publish failed for event {} ({}): {}", e.getId(), e.getEventType(), ex.getMessage());
                throw new IllegalStateException("Outbox relay failed", ex);
            }
        }
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purgePublished() {
        int removed = repository.deletePublishedBefore(Instant.now().minus(retention));
        if (removed > 0) {
            log.info("Purged {} published outbox rows", removed);
        }
    }
}
