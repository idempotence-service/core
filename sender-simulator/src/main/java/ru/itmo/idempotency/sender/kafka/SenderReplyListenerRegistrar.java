package ru.itmo.idempotency.sender.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import ru.itmo.idempotency.common.config.RouteCatalog;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.kafka.KafkaClientSupport;
import ru.itmo.idempotency.common.messaging.MessageModels;
import ru.itmo.idempotency.sender.service.SenderStateService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SenderReplyListenerRegistrar implements SmartLifecycle {

    private final RouteCatalog routeCatalog;
    private final SenderStateService senderStateService;
    private final ObjectMapper objectMapper;
    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();
    private boolean running;

    public SenderReplyListenerRegistrar(RouteCatalog routeCatalog,
                                        SenderStateService senderStateService,
                                        ObjectMapper objectMapper) {
        this.routeCatalog = routeCatalog;
        this.senderStateService = senderStateService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        routeCatalog.getAllRoutes().forEach(route -> containers.add(createContainer(route)));
        containers.forEach(ConcurrentMessageListenerContainer::start);
        running = true;
    }

    @Override
    public void stop() {
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        containers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private ConcurrentMessageListenerContainer<String, String> createContainer(RouteModels.RouteSnapshot route) {
        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(
                KafkaClientSupport.consumerProperties(
                        route.requestOut().bootstrapServers(),
                        route.requestOut().group() != null ? route.requestOut().group() : "sender-replies-" + route.integration(),
                        "sender-replies-" + route.integration()
                )
        );

        ContainerProperties containerProperties = new ContainerProperties(route.requestOut().topic());
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setMessageListener((org.springframework.kafka.listener.AcknowledgingMessageListener<String, String>) (record, acknowledgment) -> {
            processReply(route, record);
            acknowledgment.acknowledge();
        });

        ConcurrentMessageListenerContainer<String, String> container = new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setCommonErrorHandler(commonErrorHandler());
        container.getContainerProperties().setMissingTopicsFatal(false);
        return container;
    }

    private void processReply(RouteModels.RouteSnapshot route, ConsumerRecord<String, String> record) {
        try {
            MessageModels.MessageEnvelope envelope = objectMapper.readValue(record.value(), MessageModels.MessageEnvelope.class);
            senderStateService.recordReply(
                    route.integration(),
                    envelope.headers() != null && envelope.headers().get("globalKey") != null ? String.valueOf(envelope.headers().get("globalKey")) : null,
                    envelope.headers(),
                    envelope.payload()
            );
        } catch (Exception exception) {
            log.error("Failed to process sender reply for route {}", route.integration(), exception);
            throw new IllegalStateException(exception);
        }
    }

    private CommonErrorHandler commonErrorHandler() {
        return new DefaultErrorHandler((record, exception) -> log.error("Sender reply listener failed: {}", record, exception));
    }
}
