package com.sky.task;

import com.sky.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 每日清理超过 90 天的用户通知，不触碰时间轴和 Outbox 死信。 */
@Component
@ConditionalOnProperty(prefix = "sky.notification", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationRetentionTask {
    private final NotificationService notificationService;

    @Scheduled(cron = "${sky.notification.retention-cron:0 20 3 * * ?}")
    public void retain() {
        notificationService.deleteExpired();
    }
}
