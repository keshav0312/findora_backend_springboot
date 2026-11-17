package com.findora;

import com.findora.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableMongoAuditing
@EnableAsync
public class FindoraApplication {
    public static void main(String[] args) {
        SpringApplication.run(FindoraApplication.class, args);
    }
}
