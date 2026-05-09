package com.psl.oms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Order Management System backend.
 *
 * @EnableAsync      — allows @Async methods to run on background thread pools.
 * @EnableScheduling — allows @Scheduled methods (e.g. nightly delayed-order scan).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class OmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsApplication.class, args);
    }
}
