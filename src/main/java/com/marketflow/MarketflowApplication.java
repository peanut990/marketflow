package com.marketflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class MarketflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketflowApplication.class, args);
    }

}
