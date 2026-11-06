package com.bmtickets.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.bmtickets.inventory", "com.bmtickets.common"})
@EntityScan({"com.bmtickets.inventory", "com.bmtickets.common.messaging"})
@EnableJpaRepositories({"com.bmtickets.inventory", "com.bmtickets.common.messaging"})
@ConfigurationPropertiesScan
public class SeatInventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatInventoryApplication.class, args);
    }
}
