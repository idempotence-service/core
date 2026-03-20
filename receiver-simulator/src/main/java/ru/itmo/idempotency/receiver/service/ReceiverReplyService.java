package ru.itmo.idempotency.receiver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itmo.idempotency.common.config.RouteCatalog;
import ru.itmo.idempotency.common.config.RouteModels;
import ru.itmo.idempotency.common.kafka.KafkaJsonProducerRegistry;
import ru.itmo.idempotency.common.messaging.MessageModels;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReceiverReplyService {

    private final RouteCatalog routeCatalog;
    private final KafkaJsonProducerRegistry kafkaJsonProducerRegistry;

    public void sendReply(String integration, String globalKey, String result, boolean needResend, String resultDescription) {
        RouteModels.RouteSnapshot route = routeCatalog.getRequiredRoute(integration);
        if (route.replyOut() == null) {
            return;
        }
        kafkaJsonProducerRegistry.send(
                route.replyOut().bootstrapServers(),
                route.replyOut().topic(),
                globalKey,
                new MessageModels.MessageEnvelope(
                        Map.of("globalKey", globalKey),
                        new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                                new MessageModels.AsyncReplyPayload(result, needResend, resultDescription)
                        )
                )
        );
    }
}
