package com.yam.funds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FundsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundsApplication.class, args);
    }
}
