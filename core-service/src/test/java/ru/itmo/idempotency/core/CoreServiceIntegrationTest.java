package ru.itmo.idempotency.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.idempotency.common.kafka.KafkaJsonProducerRegistry;
import ru.itmo.idempotency.common.messaging.MessageModels;
import ru.itmo.idempotency.core.domain.IdempotencyEntity;
import ru.itmo.idempotency.core.domain.IdempotencyStatus;
import ru.itmo.idempotency.core.repository.IdempotencyRepository;
import ru.itmo.idempotency.core.repository.KafkaEventOutboxRepository;
import ru.itmo.idempotency.core.service.CleanupService;
import ru.itmo.idempotency.core.service.RequestDispatchProcessor;
import ru.itmo.idempotency.core.service.ReceiverDispatchProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.kafka.test.EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
        "sender.events.inbound",
        "sender.events.request-out",
        "receiver.events.unique",
        "receiver.events.reply"
})
class CoreServiceIntegrationTest {

    private static final String INBOUND_TOPIC = "sender.events.inbound";
    private static final String REQUEST_OUT_TOPIC = "sender.events.request-out";
    private static final String REPLY_IN_TOPIC = "receiver.events.unique";
    private static final String REPLY_OUT_TOPIC = "receiver.events.reply";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaJsonProducerRegistry kafkaJsonProducerRegistry;

    @Autowired
    private RequestDispatchProcessor requestDispatchProcessor;

    @Autowired
    private ReceiverDispatchProcessor receiverDispatchProcessor;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private KafkaEventOutboxRepository kafkaEventOutboxRepository;

    @Autowired
    private CleanupService cleanupService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    private Consumer<String, String> requestOutConsumer;
    private Consumer<String, String> replyInConsumer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:core;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.routes-file", () -> createRoutesFile(System.getProperty(SPRING_EMBEDDED_KAFKA_BROKERS)));
    }

    @BeforeEach
    void setUp() {
        requestOutConsumer = createConsumer("request-out-test", REQUEST_OUT_TOPIC);
        replyInConsumer = createConsumer("reply-in-test", REPLY_IN_TOPIC);
    }

    @AfterEach
    void tearDown() {
        requestOutConsumer.close();
        replyInConsumer.close();
        idempotencyRepository.deleteAll();
        kafkaEventOutboxRepository.deleteAll();
    }

    @Test
    void shouldDeduplicateInboundEventsAndSendOnlyOneUniqueMessage() throws Exception {
        String uid = UUID.randomUUID().toString();
        MessageModels.MessageEnvelope inboundMessage = new MessageModels.MessageEnvelope(
                Map.of("uid", uid, "correlationId", "corr-1"),
                objectMapper.readTree("{\"amount\":100}")
        );

        kafkaJsonProducerRegistry.send(embeddedKafkaBroker.getBrokersAsString(), INBOUND_TOPIC, uid, inboundMessage);
        kafkaJsonProducerRegistry.send(embeddedKafkaBroker.getBrokersAsString(), INBOUND_TOPIC, uid, inboundMessage);

        waitForCondition(() -> idempotencyRepository.findById("sender-service:system1-to-system2:" + uid).isPresent());
        waitForCondition(() -> kafkaEventOutboxRepository.count() == 2);

        Assertions.assertEquals(2, requestDispatchProcessor.processBatch(10));
        Assertions.assertEquals(1, receiverDispatchProcessor.processBatch(10));

        List<MessageModels.MessageEnvelope> technicalResponses = consumeMessages(requestOutConsumer, REQUEST_OUT_TOPIC, 2, Duration.ofSeconds(10));
        Assertions.assertEquals(2, technicalResponses.size());
        List<String> technicalResults = technicalResponses.stream()
                .map(message -> message.payload().path("result").asText())
                .sorted()
                .toList();
        Assertions.assertEquals(List.of("FAIL", "SUCCESS"), technicalResults);

        List<MessageModels.MessageEnvelope> receiverMessages = consumeMessages(replyInConsumer, REPLY_IN_TOPIC, 1, Duration.ofSeconds(10));
        Assertions.assertEquals(1, receiverMessages.size());
        Assertions.assertEquals(uid, receiverMessages.getFirst().headers().get("uid"));

        ConsumerRecords<String, String> additionalMessages = replyInConsumer.poll(Duration.ofSeconds(2));
        Assertions.assertTrue(additionalMessages.isEmpty(), "duplicate unique message was sent to receiver topic");

        IdempotencyEntity entity = idempotencyRepository.findById("sender-service:system1-to-system2:" + uid).orElseThrow();
        Assertions.assertEquals(IdempotencyStatus.WAITING_ASYNC_RESPONSE, entity.getStatus());
    }

    @Test
    void shouldMoveErrorEventBackToReservedAndCommitAfterSuccessReply() throws Exception {
        String uid = UUID.randomUUID().toString();
        String globalKey = "sender-service:system1-to-system2:" + uid;
        MessageModels.MessageEnvelope inboundMessage = new MessageModels.MessageEnvelope(
                Map.of("uid", uid),
                objectMapper.readTree("{\"orderId\":42}")
        );

        kafkaJsonProducerRegistry.send(embeddedKafkaBroker.getBrokersAsString(), INBOUND_TOPIC, uid, inboundMessage);
        waitForCondition(() -> idempotencyRepository.findById(globalKey).isPresent());
        waitForCondition(() -> kafkaEventOutboxRepository.count() == 1);

        requestDispatchProcessor.processBatch(10);
        receiverDispatchProcessor.processBatch(10);
        consumeMessages(replyInConsumer, REPLY_IN_TOPIC, 1, Duration.ofSeconds(10));

        kafkaJsonProducerRegistry.send(
                embeddedKafkaBroker.getBrokersAsString(),
                REPLY_OUT_TOPIC,
                globalKey,
                new MessageModels.MessageEnvelope(
                        Map.of("globalKey", globalKey),
                        objectMapper.valueToTree(new MessageModels.AsyncReplyPayload("FAIL", false, "Ошибка в С2"))
                )
        );

        waitForCondition(() -> idempotencyRepository.findById(globalKey)
                .map(entity -> entity.getStatus() == IdempotencyStatus.ERROR)
                .orElse(false));

        mockMvc.perform(post("/restart-event")
                        .header("Authorization", "Bearer operator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"globalKey\":\"" + globalKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Задача успешно перезапущена"));

        receiverDispatchProcessor.processBatch(10);
        consumeMessages(replyInConsumer, REPLY_IN_TOPIC, 1, Duration.ofSeconds(10));

        kafkaJsonProducerRegistry.send(
                embeddedKafkaBroker.getBrokersAsString(),
                REPLY_OUT_TOPIC,
                globalKey,
                new MessageModels.MessageEnvelope(
                        Map.of("globalKey", globalKey),
                        objectMapper.valueToTree(new MessageModels.AsyncReplyPayload("SUCCESS", false, "Готово"))
                )
        );

        waitForCondition(() -> idempotencyRepository.findById(globalKey)
                .map(entity -> entity.getStatus() == IdempotencyStatus.COMMITTED)
                .orElse(false));

        mockMvc.perform(get("/get-event-by-id")
                        .header("Authorization", "Bearer operator-token")
                        .param("globalKey", globalKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.globalKey").value(globalKey))
                .andExpect(jsonPath("$.data.status").value("COMMITTED"));
    }

    @Test
    void shouldReturnBusinessErrorWhenAuthorizationMissing() throws Exception {
        mockMvc.perform(get("/get-error-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TECHNICAL_ERROR_01"));
    }

    @Test
    void shouldCleanupCommittedRecordsByRetention() throws Exception {
        String uid = UUID.randomUUID().toString();
        IdempotencyEntity entity = IdempotencyEntity.builder()
                .globalKey("sender-service:system1-to-system2:" + uid)
                .sourceUid(uid)
                .serviceName("sender-service")
                .integrationName("system1-to-system2")
                .yamlSnapshot(objectMapper.readTree("{}"))
                .headers(objectMapper.readTree("{}"))
                .payload(objectMapper.readTree("{}"))
                .status(IdempotencyStatus.COMMITTED)
                .createDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3))
                .updateDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3))
                .build();
        idempotencyRepository.saveAndFlush(entity);

        int deleted = cleanupService.cleanupCommitted();

        Assertions.assertEquals(1, deleted);
        Assertions.assertEquals(0, idempotencyRepository.count());
    }

    private Consumer<String, String> createConsumer(String groupId, String topic) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(groupId + "-" + UUID.randomUUID(), "false", embeddedKafkaBroker);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new StringDeserializer()
        );
        Consumer<String, String> consumer = consumerFactory.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, true, topic);
        return consumer;
    }

    private List<MessageModels.MessageEnvelope> consumeMessages(Consumer<String, String> consumer,
                                                                String topic,
                                                                int expectedCount,
                                                                Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        List<MessageModels.MessageEnvelope> result = new ArrayList<>();
        while (System.currentTimeMillis() < deadline && result.size() < expectedCount) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records.records(topic)) {
                result.add(objectMapper.readValue(record.value(), MessageModels.MessageEnvelope.class));
            }
        }
        if (result.size() < expectedCount) {
            Assertions.fail("Expected " + expectedCount + " messages but got " + result.size());
        }
        return result;
    }

    private void waitForCondition(Check check) throws Exception {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(100);
        }
        Assertions.fail("Condition was not met within timeout");
    }

    private static String createRoutesFile(String bootstrapServers) {
        try {
            Path file = Files.createTempFile("routes-test-", ".yaml");
            Files.writeString(file, """
                    service:
                      name: sender-service

                    kafka:
                      routes:
                        system1-to-system2:
                          sender:
                            producer:
                              host: %s
                              topic: sender.events.inbound
                            consumer:
                              host: %s
                              topic: sender.events.request-out
                              group: sender-test-replies
                          receiver:
                            producer:
                              host: %s
                              topic: receiver.events.unique
                            consumer:
                              host: %s
                              topic: receiver.events.reply
                              group: core-test-replies
                          idempotency:
                            enabled: true
                    """.formatted(bootstrapServers, bootstrapServers, bootstrapServers, bootstrapServers));
            return file.toAbsolutePath().toString();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean ok() throws Exception;
    }
}
