package com.tradefinance.templateextraction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {
    "com.tradefinance.templateextraction.repository.mongo",
    "com.tradefinance.templateextraction.repository.auth"
})
@EnableMongoAuditing
public class MongoConfig {
    // Spring Boot auto-configuration handles the rest
}
