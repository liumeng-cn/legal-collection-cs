package com.legalcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LegalCollectionCsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegalCollectionCsApplication.class, args);
    }
}
