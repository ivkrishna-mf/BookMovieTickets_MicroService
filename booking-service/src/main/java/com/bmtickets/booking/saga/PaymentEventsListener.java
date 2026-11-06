package com.bmtickets.booking.saga;

import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.PaymentFailedEvent;
import com.bmtickets.common.events.PaymentRefundedEvent;
import com.bmtickets.common.events.PaymentSucceededEvent;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.KafkaRecords;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Async (event-driven) leg of the saga: payment outcome arrives via Kafka, decoupled from the HTTP request. */
@Component
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final BookingCompletionService completion;
    private final ObjectMapper mapper;

    public PaymentEventsListener(BookingCompletionService completion, ObjectMapper mapper) {
        this.completion = completion;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "booking-service", concurrency = "${app.kafka.listener-concurrency:3}")
    public void onPaymentEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String type = KafkaRecords.eventType(record);
        String eventId = KafkaRecords.eventId(record);
        switch (type == null ? "" : type) {
            case EventTypes.PAYMENT_SUCCEEDED -> {
                PaymentSucceededEvent e = mapper.readValue(record.value(), PaymentSucceededEvent.class);
                completion.onPaymentSucceeded(eventId, e.bookingId());
            }
            case EventTypes.PAYMENT_FAILED -> {
                PaymentFailedEvent e = mapper.readValue(record.value(), PaymentFailedEvent.class);
                completion.onPaymentFailed(eventId, e.bookingId(), e.reason());
            }
            case EventTypes.PAYMENT_REFUNDED -> {
                PaymentRefundedEvent e = mapper.readValue(record.value(), PaymentRefundedEvent.class);
                log.info("Refund completed for booking {}", e.bookingId());
            }
            default -> log.debug("Ignoring payment event type {}", type);
        }
    }
}
