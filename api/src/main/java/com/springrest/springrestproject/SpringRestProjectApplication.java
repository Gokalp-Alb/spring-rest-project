package com.springrest.springrestproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.springrest")
@ConfigurationPropertiesScan(basePackages = "com.springrest")
@EnableCaching
public class SpringRestProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringRestProjectApplication.class, args);
    }

}
