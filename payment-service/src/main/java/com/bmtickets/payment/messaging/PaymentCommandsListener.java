package com.bmtickets.payment.messaging;

import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.RefundRequestedCommand;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.KafkaRecords;
import com.bmtickets.payment.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Refund is naturally idempotent (state check + PSP idempotency key), so redelivery is harmless. */
@Component
public class PaymentCommandsListener {

    private final PaymentService service;
    private final ObjectMapper mapper;

    public PaymentCommandsListener(PaymentService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service")
    public void onCommand(ConsumerRecord<String, String> record) throws JsonProcessingException {
        if (EventTypes.REFUND_REQUESTED.equals(KafkaRecords.eventType(record))) {
            RefundRequestedCommand cmd = mapper.readValue(record.value(), RefundRequestedCommand.class);
            service.refund(cmd.bookingId());
        }
    }
}
