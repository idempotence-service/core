package ru.itmo.idempotency.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.idempotency.core.config.CoreProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoreSchedulers {

    private final CoreProperties coreProperties;
    private final RequestDispatchProcessor requestDispatchProcessor;
    private final ReceiverDispatchProcessor receiverDispatchProcessor;
    private final CleanupService cleanupService;

    @Scheduled(fixedDelayString = "${app.scheduler.outbox-fixed-delay:5s}")
    public void processOutbox() {
        requestDispatchProcessor.processBatch(coreProperties.getScheduler().getBatchSize());
    }

    @Scheduled(fixedDelayString = "${app.scheduler.delivery-fixed-delay:5s}")
    public void processDelivery() {
        receiverDispatchProcessor.processBatch(coreProperties.getScheduler().getBatchSize());
    }

    @Scheduled(fixedDelayString = "${app.scheduler.cleanup-fixed-delay:1d}")
    public void cleanupCommitted() {
        cleanupService.cleanupCommitted();
    }
}
