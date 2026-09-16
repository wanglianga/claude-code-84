package com.port.inspection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PortInspectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortInspectionApplication.class, args);
    }
}
