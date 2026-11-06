package com.bmtickets.payment.config;

import com.bmtickets.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MessagingConfig {

    @Bean
    NewTopic paymentEventsTopic(@Value("${app.kafka.partitions:12}") int partitions,
                                @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(partitions).replicas(replicas)
                .config("min.insync.replicas", replicas > 1 ? "2" : "1").build();
    }
}
