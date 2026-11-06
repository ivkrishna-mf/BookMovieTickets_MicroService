package com.bmtickets.inventory.messaging;

import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.ShowScheduledEvent;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.InboxGuard;
import com.bmtickets.common.messaging.KafkaRecords;
import com.bmtickets.inventory.domain.Seat;
import com.bmtickets.inventory.domain.SeatRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Event-driven seat map creation (async: the admin does not wait for thousands of seat rows to be written). */
@Component
public class ShowEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ShowEventsListener.class);
    private static final String CONSUMER = "seat-inventory";

    private final SeatRepository seats;
    private final InboxGuard inbox;
    private final ObjectMapper mapper;

    public ShowEventsListener(SeatRepository seats, InboxGuard inbox, ObjectMapper mapper) {
        this.seats = seats;
        this.inbox = inbox;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.SHOW_EVENTS, groupId = "seat-inventory-service")
    @Transactional
    public void onShowEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        if (!EventTypes.SHOW_SCHEDULED.equals(KafkaRecords.eventType(record))) {
            return;
        }
        if (!inbox.firstDelivery(CONSUMER, KafkaRecords.eventId(record))) {
            log.debug("Duplicate ShowScheduled delivery ignored: {}", KafkaRecords.eventId(record));
            return;
        }
        ShowScheduledEvent event = mapper.readValue(record.value(), ShowScheduledEvent.class);
        if (seats.existsByShowId(event.showId())) {
            return;
        }
        List<Seat> generated = new ArrayList<>();
        for (ShowScheduledEvent.SeatSection section : event.sections()) {
            for (String row : section.rows()) {
                for (int i = 1; i <= section.seatsPerRow(); i++) {
                    generated.add(new Seat(event.showId(), row, i, section.category()));
                }
            }
        }
        seats.saveAll(generated);   // JDBC batched (hibernate.jdbc.batch_size)
        log.info("Generated {} seats for show {}", generated.size(), event.showId());
    }
}
