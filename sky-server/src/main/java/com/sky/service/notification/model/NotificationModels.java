package com.sky.service.notification.model;

/** 通知与 Outbox 的稳定领域模型。 */
public final class NotificationModels {
    private NotificationModels() {
    }

    public enum NotificationType { ORDER, PAYMENT, REFUND, AFTER_SALE, SYSTEM }
    public enum OutboxStatus { PENDING, RETRY, PUBLISHED, DEAD }

    public record NotificationCommand(String businessKey, long userId, NotificationType type,
                                      String title, String content, Long orderId) {
    }
}
