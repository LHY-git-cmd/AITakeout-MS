package com.sky.task;

import com.sky.service.notification.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 定时发布用户通知 Outbox。 */
@Component
@ConditionalOnProperty(prefix = "sky.notification", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxPublishTask {
    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${sky.notification.outbox-delay-ms:5000}")
    public void publish() {
        publisher.publishBatch();
    }
}
