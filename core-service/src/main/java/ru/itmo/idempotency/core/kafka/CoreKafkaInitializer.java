package ru.itmo.idempotency.core.kafka;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.itmo.idempotency.common.config.RouteCatalog;
import ru.itmo.idempotency.common.kafka.KafkaTopicProvisioner;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CoreKafkaInitializer {

    @Bean
    ApplicationRunner coreKafkaTopicsInitializer(RouteCatalog routeCatalog, KafkaTopicProvisioner kafkaTopicProvisioner) {
        return arguments -> {
            List<ru.itmo.idempotency.common.config.RouteModels.RouteChannel> channels = new ArrayList<>();
            routeCatalog.getEnabledRoutes().forEach(route -> {
                channels.add(route.inbound());
                channels.add(route.requestOut());
                channels.add(route.replyIn());
                channels.add(route.replyOut());
            });
            kafkaTopicProvisioner.ensureTopics(channels);
        };
    }
}
