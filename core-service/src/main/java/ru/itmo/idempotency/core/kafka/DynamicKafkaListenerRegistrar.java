package ru.itmo.idempotency.core.kafka;

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
import ru.itmo.idempotency.core.service.AsyncReplyProcessor;
import ru.itmo.idempotency.core.service.InboundMessageProcessor;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DynamicKafkaListenerRegistrar implements SmartLifecycle {

    private final RouteCatalog routeCatalog;
    private final InboundMessageProcessor inboundMessageProcessor;
    private final AsyncReplyProcessor asyncReplyProcessor;
    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();

    private boolean running;

    public DynamicKafkaListenerRegistrar(RouteCatalog routeCatalog,
                                         InboundMessageProcessor inboundMessageProcessor,
                                         AsyncReplyProcessor asyncReplyProcessor) {
        this.routeCatalog = routeCatalog;
        this.inboundMessageProcessor = inboundMessageProcessor;
        this.asyncReplyProcessor = asyncReplyProcessor;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        for (RouteModels.RouteSnapshot route : routeCatalog.getEnabledRoutes()) {
            containers.add(createContainer(
                    route.inbound(),
                    route.inbound().group() != null ? route.inbound().group() : "core-inbound-" + route.integration(),
                    "core-inbound-" + route.integration(),
                    record -> inboundMessageProcessor.handle(route, record.value())
            ));

            if (route.replyOut() != null) {
                containers.add(createContainer(
                        route.replyOut(),
                        route.replyOut().group() != null ? route.replyOut().group() : "core-reply-" + route.integration(),
                        "core-reply-" + route.integration(),
                        record -> asyncReplyProcessor.handle(route, record.value())
                ));
            }
        }

        containers.forEach(ConcurrentMessageListenerContainer::start);
        running = true;
        log.info("Registered {} Kafka listener containers", containers.size());
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

    private ConcurrentMessageListenerContainer<String, String> createContainer(RouteModels.RouteChannel channel,
                                                                                String groupId,
                                                                                String clientIdPrefix,
                                                                                java.util.function.Consumer<ConsumerRecord<String, String>> processor) {
        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(
                KafkaClientSupport.consumerProperties(channel.bootstrapServers(), groupId, clientIdPrefix)
        );

        ContainerProperties containerProperties = new ContainerProperties(channel.topic());
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setMessageListener((org.springframework.kafka.listener.AcknowledgingMessageListener<String, String>) (record, acknowledgment) -> {
            try {
                processor.accept(record);
                acknowledgment.acknowledge();
            } catch (Exception exception) {
                log.error("Kafka listener processing failed for topic {}", channel.topic(), exception);
                throw exception;
            }
        });

        ConcurrentMessageListenerContainer<String, String> container = new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setCommonErrorHandler(commonErrorHandler());
        container.getContainerProperties().setMissingTopicsFatal(false);
        return container;
    }

    private CommonErrorHandler commonErrorHandler() {
        return new DefaultErrorHandler((consumerRecord, exception) ->
                log.error("Message permanently failed after retries: {}", consumerRecord, exception));
    }
}
