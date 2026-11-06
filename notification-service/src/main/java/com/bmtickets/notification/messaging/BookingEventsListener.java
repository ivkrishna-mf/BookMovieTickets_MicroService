package com.bmtickets.notification.messaging;

import com.bmtickets.common.events.BookingCancelledEvent;
import com.bmtickets.common.events.BookingConfirmedEvent;
import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.KafkaRecords;
import com.bmtickets.notification.email.TicketEmailRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Asynchronous ticket confirmation. Non-blocking retries: a failing message moves to
 * booking.events-retry-0/1/2 (2s, 6s, 18s back-off) and finally booking.events.DLT, so a slow/failing e-mail
 * provider never blocks other customers' confirmations on the main partition.
 */
@Component
public class BookingEventsListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventsListener.class);

    private final NotificationDispatcher dispatcher;
    private final TicketEmailRenderer renderer;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public BookingEventsListener(NotificationDispatcher dispatcher, TicketEmailRenderer renderer, ObjectMapper mapper,
                                 MeterRegistry metrics) {
        this.dispatcher = dispatcher;
        this.renderer = renderer;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 3.0),
            dltTopicSuffix = ".DLT",
            numPartitions = "${app.kafka.partitions:12}",
            replicationFactor = "${app.kafka.replicas:1}",
            exclude = {JsonProcessingException.class, IllegalArgumentException.class})
    @KafkaListener(topics = Topics.BOOKING_EVENTS, groupId = "notification-service",
            concurrency = "${app.kafka.listener-concurrency:3}")
    public void onBookingEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String type = KafkaRecords.eventType(record);
        if (EventTypes.BOOKING_CONFIRMED.equals(type)) {
            BookingConfirmedEvent e = mapper.readValue(record.value(), BookingConfirmedEvent.class);
            dispatcher.sendOnce(e.bookingId(), "TICKET_CONFIRMATION", e.userEmail(), renderer.confirmed(e));
        } else if (EventTypes.BOOKING_CANCELLED.equals(type)) {
            BookingCancelledEvent e = mapper.readValue(record.value(), BookingCancelledEvent.class);
            dispatcher.sendOnce(e.bookingId(), "BOOKING_CANCELLED", e.userEmail(), renderer.cancelled(e));
        }
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        metrics.counter("notification.dlt.total").increment();   // CloudWatch/Grafana alarm on this
        log.error("Notification parked on DLT: key={} type={}", record.key(), KafkaRecords.eventType(record));
    }
}
