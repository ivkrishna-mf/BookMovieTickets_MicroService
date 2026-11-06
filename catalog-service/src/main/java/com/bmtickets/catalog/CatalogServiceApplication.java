package com.bmtickets.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.bmtickets.catalog", "com.bmtickets.common"})
@EntityScan({"com.bmtickets.catalog", "com.bmtickets.common.messaging"})
@EnableJpaRepositories({"com.bmtickets.catalog", "com.bmtickets.common.messaging"})
@EnableCaching
public class CatalogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
