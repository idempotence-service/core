package ru.itmo.idempotency.sender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"ru.itmo.idempotency.sender", "ru.itmo.idempotency.common"})
@ConfigurationPropertiesScan
public class SenderSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SenderSimulatorApplication.class, args);
    }
}
