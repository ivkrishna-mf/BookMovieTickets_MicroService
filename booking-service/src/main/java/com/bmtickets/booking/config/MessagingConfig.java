package com.bmtickets.booking.config;

import com.bmtickets.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MessagingConfig {

    @Value("${app.kafka.partitions:12}")
    private int partitions;
    @Value("${app.kafka.replicas:1}")
    private int replicas;

    @Bean
    NewTopic bookingEventsTopic() {
        return topic(Topics.BOOKING_EVENTS);
    }

    @Bean
    NewTopic paymentCommandsTopic() {
        return topic(Topics.PAYMENT_COMMANDS);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas)
                .config("min.insync.replicas", replicas > 1 ? "2" : "1").build();
    }
}
