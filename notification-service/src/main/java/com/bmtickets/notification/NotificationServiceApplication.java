package com.bmtickets.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.bmtickets.notification", "com.bmtickets.common"})
@EntityScan({"com.bmtickets.notification", "com.bmtickets.common.messaging"})
@EnableJpaRepositories({"com.bmtickets.notification", "com.bmtickets.common.messaging"})
@ConfigurationPropertiesScan
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
