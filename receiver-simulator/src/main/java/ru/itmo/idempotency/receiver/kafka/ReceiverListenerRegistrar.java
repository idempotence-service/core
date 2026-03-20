package ru.itmo.idempotency.receiver.kafka;

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
import ru.itmo.idempotency.receiver.service.ReceiverMode;
import ru.itmo.idempotency.receiver.service.ReceiverReplyService;
import ru.itmo.idempotency.receiver.service.ReceiverStateService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ReceiverListenerRegistrar implements SmartLifecycle {

    private final RouteCatalog routeCatalog;
    private final ReceiverStateService receiverStateService;
    private final ReceiverReplyService receiverReplyService;
    private final ObjectMapper objectMapper;
    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();
    private boolean running;

    public ReceiverListenerRegistrar(RouteCatalog routeCatalog,
                                     ReceiverStateService receiverStateService,
                                     ReceiverReplyService receiverReplyService,
                                     ObjectMapper objectMapper) {
        this.routeCatalog = routeCatalog;
        this.receiverStateService = receiverStateService;
        this.receiverReplyService = receiverReplyService;
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
                        route.replyIn().bootstrapServers(),
                        "receiver-" + route.integration(),
                        "receiver-" + route.integration()
                )
        );

        ContainerProperties containerProperties = new ContainerProperties(route.replyIn().topic());
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setMessageListener((org.springframework.kafka.listener.AcknowledgingMessageListener<String, String>) (record, acknowledgment) -> {
            processIncoming(route, record);
            acknowledgment.acknowledge();
        });

        ConcurrentMessageListenerContainer<String, String> container = new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setCommonErrorHandler(commonErrorHandler());
        container.getContainerProperties().setMissingTopicsFatal(false);
        return container;
    }

    private void processIncoming(RouteModels.RouteSnapshot route, ConsumerRecord<String, String> record) {
        try {
            MessageModels.MessageEnvelope envelope = objectMapper.readValue(record.value(), MessageModels.MessageEnvelope.class);
            String globalKey = envelope.headers() != null && envelope.headers().get("globalKey") != null
                    ? String.valueOf(envelope.headers().get("globalKey"))
                    : record.key();
            ReceiverStateService.ReceivedMessage message = receiverStateService.record(route.integration(), globalKey, envelope.headers(), envelope.payload());
            processAutoReply(message);
        } catch (Exception exception) {
            log.error("Failed to process receiver message for route {}", route.integration(), exception);
            throw new IllegalStateException(exception);
        }
    }

    private void processAutoReply(ReceiverStateService.ReceivedMessage message) {
        ReceiverMode mode = receiverStateService.modeFor(message.integration());
        switch (mode) {
            case AUTO_SUCCESS -> receiverReplyService.sendReply(message.integration(), message.globalKey(), "SUCCESS", false, "Обработка завершена успешно");
            case AUTO_FAIL_RESEND -> receiverReplyService.sendReply(message.integration(), message.globalKey(), "FAIL", true, "Нужна повторная отправка");
            case AUTO_FAIL_NO_RESEND -> receiverReplyService.sendReply(message.integration(), message.globalKey(), "FAIL", false, "Обработка завершилась ошибкой");
            case MANUAL -> {
            }
        }
    }

    private CommonErrorHandler commonErrorHandler() {
        return new DefaultErrorHandler((record, exception) -> log.error("Receiver listener failed: {}", record, exception));
    }
}
