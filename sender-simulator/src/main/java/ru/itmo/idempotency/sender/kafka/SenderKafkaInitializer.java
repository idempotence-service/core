package ru.itmo.idempotency.sender.kafka;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.itmo.idempotency.common.config.RouteCatalog;
import ru.itmo.idempotency.common.kafka.KafkaTopicProvisioner;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SenderKafkaInitializer {

    @Bean
    ApplicationRunner senderKafkaTopicsInitializer(RouteCatalog routeCatalog, KafkaTopicProvisioner kafkaTopicProvisioner) {
        return args -> {
            List<ru.itmo.idempotency.common.config.RouteModels.RouteChannel> channels = new ArrayList<>();
            routeCatalog.getAllRoutes().forEach(route -> {
                channels.add(route.inbound());
                channels.add(route.requestOut());
            });
            kafkaTopicProvisioner.ensureTopics(channels);
        };
    }
}
