package ru.itmo.idempotency.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.stereotype.Component;
import ru.itmo.idempotency.common.config.RouteModels;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class KafkaTopicProvisioner {

    public void ensureTopics(Collection<RouteModels.RouteChannel> channels) {
        Map<String, NewTopic> uniqueTopics = new LinkedHashMap<>();
        for (RouteModels.RouteChannel channel : channels) {
            if (channel == null) {
                continue;
            }
            uniqueTopics.put(channel.bootstrapServers() + "::" + channel.topic(), new NewTopic(channel.topic(), 1, (short) 1));
        }

        Map<String, Map<String, NewTopic>> byBootstrap = new LinkedHashMap<>();
        for (RouteModels.RouteChannel channel : channels) {
            if (channel == null) {
                continue;
            }
            byBootstrap.computeIfAbsent(channel.bootstrapServers(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(channel.topic(), new NewTopic(channel.topic(), 1, (short) 1));
        }

        byBootstrap.forEach(this::createTopicsForBootstrap);
    }

    private void createTopicsForBootstrap(String bootstrapServers, Map<String, NewTopic> topics) {
        try (AdminClient adminClient = AdminClient.create(KafkaClientSupport.adminProperties(bootstrapServers))) {
            adminClient.createTopics(topics.values()).all().get();
            log.info("Provisioned {} Kafka topics on {}", topics.size(), bootstrapServers);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka topic provisioning interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof TopicExistsException) {
                log.debug("Kafka topics already exist on {}", bootstrapServers);
                return;
            }
            throw new IllegalStateException("Kafka topic provisioning failed for " + bootstrapServers, exception.getCause());
        }
    }
}
