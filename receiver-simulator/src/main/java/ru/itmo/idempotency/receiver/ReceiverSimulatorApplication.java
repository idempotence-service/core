package ru.itmo.idempotency.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"ru.itmo.idempotency.receiver", "ru.itmo.idempotency.common"})
@ConfigurationPropertiesScan
public class ReceiverSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceiverSimulatorApplication.class, args);
    }
}
