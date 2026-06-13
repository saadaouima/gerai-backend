package com.gerai.demandesservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemandesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemandesServiceApplication.class, args);
    }

}
