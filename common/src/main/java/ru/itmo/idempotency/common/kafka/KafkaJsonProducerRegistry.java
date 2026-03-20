package ru.itmo.idempotency.common.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaJsonProducerRegistry implements DisposableBean {

    private final ObjectMapper objectMapper;
    private final Map<String, KafkaTemplate<String, String>> templates = new ConcurrentHashMap<>();
    private final Map<String, DefaultKafkaProducerFactory<String, String>> factories = new ConcurrentHashMap<>();

    public KafkaJsonProducerRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void send(String bootstrapServers, String topic, String key, Object payload) {
        KafkaTemplate<String, String> template = templates.computeIfAbsent(bootstrapServers, this::createTemplate);
        try {
            template.send(topic, key, objectMapper.writeValueAsString(payload))
                    .get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka send was interrupted", exception);
        } catch (ExecutionException | TimeoutException | JsonProcessingException exception) {
            throw new IllegalStateException("Kafka send failed", exception);
        }
    }

    private KafkaTemplate<String, String> createTemplate(String bootstrapServers) {
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(
                KafkaClientSupport.producerProperties(bootstrapServers)
        );
        factories.put(bootstrapServers, factory);
        return new KafkaTemplate<>(factory);
    }

    @Override
    public void destroy() {
        factories.values().forEach(DefaultKafkaProducerFactory::destroy);
        factories.clear();
        templates.clear();
    }
}
