package com.tradefinance.templateextraction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.tradefinance.templateextraction")
public class TemplateExtractionApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateExtractionApplication.class, args);
    }
}
