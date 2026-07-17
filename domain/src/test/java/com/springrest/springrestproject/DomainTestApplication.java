package com.springrest.springrestproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.springrest")
@ConfigurationPropertiesScan(basePackages = "com.springrest")
public class DomainTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomainTestApplication.class, args);
    }
}
